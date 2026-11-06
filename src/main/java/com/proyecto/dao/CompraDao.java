package com.proyecto.dao;

import com.proyecto.modelo.Compra;
import com.proyecto.modelo.DetalleCompra;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a las tablas compras y detalle_compra.
 * Registrar una compra es una TRANSACCIÓN: o se guarda todo, o no se guarda nada.
 */
public class CompraDao {

    //listar todas las compras.
    // Columnas que lee Java: id_compra, fecha_compra, id_proveedor
    private static final String SQL_LISTAR = """
            SELECT id_compra, fecha_compra, id_proveedor
            FROM compras
            ORDER BY id_compra;
            """;

    //listar TODA la tabla detalle_compra.
    // Columnas que lee Java: id_compra, id_producto, cantidad, costo_unitario
    private static final String SQL_LISTAR_DETALLES = """
            SELECT id_compra, id_producto, cantidad, costo_unitario
            FROM detalle_compra
            ORDER BY id_compra, id_producto;
            """;

    //listar los detalles de UNA compra.
    // Parámetros: 1 = id_compra
    // Columnas que lee Java: id_compra, id_producto, cantidad, costo_unitario
    private static final String SQL_DETALLES_DE_COMPRA = """
            SELECT id_compra, id_producto, cantidad, costo_unitario
            FROM detalle_compra
            WHERE id_compra = ?
            ORDER BY id_producto;
            """;

    //insertar la compra y DEVOLVER su id generado (usa RETURNING id_compra).
    // Parámetros: 1 = id_proveedor   (fecha_compra toma su DEFAULT)
    private static final String SQL_INSERTAR_COMPRA = """
            INSERT INTO compras (id_proveedor) VALUES (?)
            RETURNING id_compra;
            """;

    //insertar un renglón en detalle_compra.
    // Parámetros: 1 = id_compra, 2 = id_producto, 3 = cantidad, 4 = costo_unitario
    private static final String SQL_INSERTAR_DETALLE = """
            INSERT INTO detalle_compra (id_compra, id_producto, cantidad, costo_unitario)
            VALUES (?, ?, ?, ?);
            """;

    //SUMAR la cantidad comprada a las existencias del producto.
    // Parámetros: 1 = cantidad, 2 = id_producto
    private static final String SQL_SUMAR_EXISTENCIAS = """
            UPDATE productos SET existencias = existencias + ? WHERE id_producto = ?;
            """;

    private final Connection conn;

    public CompraDao(Connection conn) {
        this.conn = conn;
    }

    public List<Compra> listar() throws SQLException {
        List<Compra> compras = new ArrayList<>();
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_LISTAR, "CompraDao.SQL_LISTAR");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                compras.add(new Compra(
                        rs.getInt("id_compra"),
                        rs.getObject("fecha_compra", OffsetDateTime.class),
                        rs.getInt("id_proveedor")));
            }
        }
        return compras;
    }

    public List<DetalleCompra> listarDetalles() throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_LISTAR_DETALLES, "CompraDao.SQL_LISTAR_DETALLES")) {
            return leerDetalles(ps);
        }
    }

    public List<DetalleCompra> detallesDe(int idCompra) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_DETALLES_DE_COMPRA, "CompraDao.SQL_DETALLES_DE_COMPRA")) {
            ps.setInt(1, idCompra);
            return leerDetalles(ps);
        }
    }

    /**
     * Guarda la compra, sus detalles y suma existencias en una sola transacción.
     * El idCompra de cada detalle recibido se ignora: se usa el que genera la base de datos.
     *
     * @return el id de la compra creada.
     */
    public int registrarCompra(int idProveedor, List<DetalleCompra> detalles) throws SQLException {
        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false); // 1. Inicia la transacción
        try {
            int idCompra = insertarCompra(idProveedor); // 2. Cabecera de la compra

            try (PreparedStatement psDetalle = JdbcUtil.preparar(conn, SQL_INSERTAR_DETALLE,
                         "CompraDao.SQL_INSERTAR_DETALLE");
                 PreparedStatement psExistencias = JdbcUtil.preparar(conn, SQL_SUMAR_EXISTENCIAS,
                         "CompraDao.SQL_SUMAR_EXISTENCIAS")) {
                for (DetalleCompra d : detalles) {
                    // 3. Renglón del detalle
                    psDetalle.setInt(1, idCompra);
                    psDetalle.setInt(2, d.idProducto());
                    psDetalle.setInt(3, d.cantidad());
                    psDetalle.setBigDecimal(4, d.costoUnitario());
                    psDetalle.executeUpdate();

                    // 4. Aumento de inventario
                    psExistencias.setInt(1, d.cantidad());
                    psExistencias.setInt(2, d.idProducto());
                    if (psExistencias.executeUpdate() == 0) {
                        throw new SQLException("El producto con id " + d.idProducto() + " ya no existe.");
                    }
                }
            }

            conn.commit(); // 5. Todo salió bien: se confirma
            return idCompra;
        } catch (SQLException e) {
            conn.rollback(); // Algo falló: se deshace todo lo de esta compra
            throw e;
        } finally {
            conn.setAutoCommit(autoCommitOriginal); // 6. Deja la conexión como estaba
        }
    }

    private int insertarCompra(int idProveedor) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_INSERTAR_COMPRA, "CompraDao.SQL_INSERTAR_COMPRA")) {
            ps.setInt(1, idProveedor);
            try (ResultSet rs = ps.executeQuery()) { // RETURNING devuelve una fila con el id
                if (!rs.next()) {
                    throw new SQLException("SQL_INSERTAR_COMPRA no devolvió el id (¿falta RETURNING id_compra?).");
                }
                return rs.getInt(1);
            }
        }
    }

    private static List<DetalleCompra> leerDetalles(PreparedStatement ps) throws SQLException {
        List<DetalleCompra> detalles = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                detalles.add(new DetalleCompra(
                        rs.getInt("id_compra"),
                        rs.getInt("id_producto"),
                        rs.getInt("cantidad"),
                        rs.getBigDecimal("costo_unitario")));
            }
        }
        return detalles;
    }
}
