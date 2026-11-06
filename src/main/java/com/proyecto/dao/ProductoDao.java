package com.proyecto.dao;

import com.proyecto.modelo.Producto;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla productos.
 * Solo ejecuta SQL: no imprime ni lee del teclado. Los errores se lanzan como
 * SQLException para que el menú decida qué mensaje mostrar.
 */
public class ProductoDao {

    private static final String SQL_LISTAR = """
            SELECT p.id_producto, p.sku, p.nombre, p.precio, p.existencias,
                   p.id_categoria, c.nombre_categoria
            FROM productos p
            JOIN categorias c ON p.id_categoria = c.id_categoria
            ORDER BY p.nombre;
            """;

    // Buscar UN producto por su SKU (con JOIN a categorias, igual que SQL_LISTAR).
    // Parámetros: 1 = sku
    // Columnas que lee Java: id_producto, sku, nombre, precio, existencias, id_categoria, nombre_categoria
    private static final String SQL_BUSCAR_POR_SKU = """
            SELECT p.id_producto, p.sku, p.nombre, p.precio, p.existencias,
                   p.id_categoria, c.nombre_categoria
            FROM productos p
            JOIN categorias c ON p.id_categoria = c.id_categoria
            WHERE p.sku = ?;
            """;

    private static final String SQL_INSERTAR = """
            INSERT INTO productos (sku, nombre, precio, existencias, id_categoria)
            VALUES (?, ?, ?, ?, ?);
            """;

    private static final String SQL_ACTUALIZAR = """
            UPDATE productos SET precio = ?, existencias = ? WHERE sku = ?;
            """;

    private static final String SQL_ELIMINAR = """
            DELETE FROM productos WHERE sku = ?;
            """;

    private final Connection conn;

    public ProductoDao(Connection conn) {
        this.conn = conn;
    }

    public List<Producto> listar() throws SQLException {
        List<Producto> productos = new ArrayList<>();
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_LISTAR, "ProductoDao.SQL_LISTAR");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                productos.add(mapear(rs));
            }
        }
        return productos;
    }

    /** @return el producto, o Optional.empty() si el SKU no existe. */
    public Optional<Producto> buscarPorSku(String sku) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_BUSCAR_POR_SKU, "ProductoDao.SQL_BUSCAR_POR_SKU")) {
            ps.setString(1, sku);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    /** @return filas insertadas (1 si todo salió bien). */
    public int agregar(String sku, String nombre, BigDecimal precio, int existencias, int idCategoria)
            throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_INSERTAR, "ProductoDao.SQL_INSERTAR")) {
            ps.setString(1, sku);
            ps.setString(2, nombre);
            ps.setBigDecimal(3, precio);
            ps.setInt(4, existencias);
            ps.setInt(5, idCategoria);
            return ps.executeUpdate();
        }
    }

    /** @return filas actualizadas (0 si el SKU no existe). */
    public int actualizarPrecioYExistencias(String sku, BigDecimal precio, int existencias)
            throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_ACTUALIZAR, "ProductoDao.SQL_ACTUALIZAR")) {
            ps.setBigDecimal(1, precio);
            ps.setInt(2, existencias);
            ps.setString(3, sku);
            return ps.executeUpdate();
        }
    }

    /**
     * Sin ON DELETE CASCADE: si detalle_venta o detalle_compra referencian al producto,
     * PostgreSQL rechaza el DELETE (SQLSTATE 23503) y el historial queda intacto.
     *
     * @return filas eliminadas (0 si el SKU no existe).
     */
    public int eliminar(String sku) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_ELIMINAR, "ProductoDao.SQL_ELIMINAR")) {
            ps.setString(1, sku);
            return ps.executeUpdate();
        }
    }

    private static Producto mapear(ResultSet rs) throws SQLException {
        return new Producto(
                rs.getInt("id_producto"),
                rs.getString("sku"),
                rs.getString("nombre"),
                rs.getBigDecimal("precio"),
                rs.getInt("existencias"),
                rs.getInt("id_categoria"),
                rs.getString("nombre_categoria"));
    }
}
