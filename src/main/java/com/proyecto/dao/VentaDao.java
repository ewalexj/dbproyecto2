package com.proyecto.dao;

import com.proyecto.modelo.DetalleVenta;
import com.proyecto.modelo.Venta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a las tablas ventas y detalle_venta.
 * Registrar una venta es una TRANSACCIÓN: o se guarda todo, o no se guarda nada.
 */
public class VentaDao {

    //listar todas las ventas.
    // Columnas que lee Java: id_venta, fecha_venta, id_cliente
    private static final String SQL_LISTAR = """
            SELECT id_venta, fecha_venta, id_cliente
            FROM ventas
            ORDER BY id_venta;
            """;

    //listar TODA la tabla detalle_venta.
    // Columnas que lee Java: id_venta, id_producto, cantidad, precio_unitario
    private static final String SQL_LISTAR_DETALLES = """
            SELECT id_venta, id_producto, cantidad, precio_unitario
            FROM detalle_venta
            ORDER BY id_venta, id_producto;
            """;

    //listar los detalles de UNA venta.
    // Parámetros: 1 = id_venta
    // Columnas que lee Java: id_venta, id_producto, cantidad, precio_unitario
    private static final String SQL_DETALLES_DE_VENTA = """
            SELECT id_venta, id_producto, cantidad, precio_unitario
            FROM detalle_venta
            WHERE id_venta = ?
            ORDER BY id_producto;
            """;

    //insertar la venta y DEVOLVER su id generado (usa RETURNING id_venta).
    // Parámetros: 1 = id_cliente   (fecha_venta toma su DEFAULT)
    private static final String SQL_INSERTAR_VENTA = """
            INSERT INTO ventas (id_cliente) VALUES (?)
            RETURNING id_venta;
            """;

    //insertar un renglón en detalle_venta.
    // Parámetros: 1 = id_venta, 2 = id_producto, 3 = cantidad, 4 = precio_unitario
    private static final String SQL_INSERTAR_DETALLE = """
            INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario)
            VALUES (?, ?, ?, ?);
            """;

    //RESTAR la cantidad vendida a las existencias del producto.
    // Parámetros: 1 = cantidad, 2 = id_producto
    // Si no alcanza, el CHECK (existencias >= 0) falla con 23514 y se revierte todo.
    private static final String SQL_DESCONTAR_EXISTENCIAS = """
            UPDATE productos SET existencias = existencias - ? WHERE id_producto = ?;
            """;

    private final Connection conn;

    public VentaDao(Connection conn) {
        this.conn = conn;
    }

    public List<Venta> listar() throws SQLException {
        List<Venta> ventas = new ArrayList<>();
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_LISTAR, "VentaDao.SQL_LISTAR");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ventas.add(new Venta(
                        rs.getInt("id_venta"),
                        rs.getObject("fecha_venta", OffsetDateTime.class),
                        rs.getInt("id_cliente")));
            }
        }
        return ventas;
    }

    public List<DetalleVenta> listarDetalles() throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_LISTAR_DETALLES, "VentaDao.SQL_LISTAR_DETALLES")) {
            return leerDetalles(ps);
        }
    }

    public List<DetalleVenta> detallesDe(int idVenta) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_DETALLES_DE_VENTA, "VentaDao.SQL_DETALLES_DE_VENTA")) {
            ps.setInt(1, idVenta);
            return leerDetalles(ps);
        }
    }

    /**
     * Guarda la venta, sus detalles y descuenta existencias en una sola transacción.
     * El idVenta de cada detalle recibido se ignora: se usa el que genera la base de datos.
     *
     * @return el id de la venta creada.
     */
    public int registrarVenta(int idCliente, List<DetalleVenta> detalles) throws SQLException {
        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false); // 1. Inicia la transacción
        try {
            int idVenta = insertarVenta(idCliente); // 2. Cabecera de la venta

            try (PreparedStatement psDetalle = JdbcUtil.preparar(conn, SQL_INSERTAR_DETALLE,
                         "VentaDao.SQL_INSERTAR_DETALLE");
                 PreparedStatement psExistencias = JdbcUtil.preparar(conn, SQL_DESCONTAR_EXISTENCIAS,
                         "VentaDao.SQL_DESCONTAR_EXISTENCIAS")) {
                for (DetalleVenta d : detalles) {
                    // 3. Renglón del detalle
                    psDetalle.setInt(1, idVenta);
                    psDetalle.setInt(2, d.idProducto());
                    psDetalle.setInt(3, d.cantidad());
                    psDetalle.setBigDecimal(4, d.precioUnitario());
                    psDetalle.executeUpdate();

                    // 4. Descuento de inventario
                    psExistencias.setInt(1, d.cantidad());
                    psExistencias.setInt(2, d.idProducto());
                    if (psExistencias.executeUpdate() == 0) {
                        throw new SQLException("El producto con id " + d.idProducto() + " ya no existe.");
                    }
                }
            }

            conn.commit(); // 5. Todo salió bien: se confirma
            return idVenta;
        } catch (SQLException e) {
            conn.rollback(); // Algo falló: se deshace todo lo de esta venta
            throw e;
        } finally {
            conn.setAutoCommit(autoCommitOriginal); // 6. Deja la conexión como estaba
        }
    }

    private int insertarVenta(int idCliente) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_INSERTAR_VENTA, "VentaDao.SQL_INSERTAR_VENTA")) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) { // RETURNING devuelve una fila con el id
                if (!rs.next()) {
                    throw new SQLException("SQL_INSERTAR_VENTA no devolvió el id (¿falta RETURNING id_venta?).");
                }
                return rs.getInt(1);
            }
        }
    }

    private static List<DetalleVenta> leerDetalles(PreparedStatement ps) throws SQLException {
        List<DetalleVenta> detalles = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                detalles.add(new DetalleVenta(
                        rs.getInt("id_venta"),
                        rs.getInt("id_producto"),
                        rs.getInt("cantidad"),
                        rs.getBigDecimal("precio_unitario")));
            }
        }
        return detalles;
    }
}
