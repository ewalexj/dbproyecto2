package com.proyecto.dao;

import com.proyecto.modelo.Proveedor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla proveedores.
 * El esquema exige correo o teléfono (al menos uno): si faltan ambos, falla el CHECK (23514).
 * Ambos son UNIQUE: un duplicado da 23505.
 */
public class ProveedorDao {

    //listar todos los proveedores.
    // Columnas que lee Java: id_proveedor, nombre_proveedor, correo_proveedor, telefono_proveedor
    private static final String SQL_LISTAR = """
            SELECT id_proveedor, nombre_proveedor, correo_proveedor, telefono_proveedor
            FROM proveedores
            ORDER BY id_proveedor;
            """;

    //buscar UN proveedor por su id.
    // Parámetros: 1 = id_proveedor
    // Columnas que lee Java: id_proveedor, nombre_proveedor, correo_proveedor, telefono_proveedor
    private static final String SQL_BUSCAR_POR_ID = """
            SELECT id_proveedor, nombre_proveedor, correo_proveedor, telefono_proveedor
            FROM proveedores
            WHERE id_proveedor = ?;
            """;

    //insertar un proveedor.
    // Parámetros: 1 = nombre_proveedor, 2 = correo_proveedor, 3 = telefono_proveedor
    private static final String SQL_INSERTAR = """
            INSERT INTO proveedores (nombre_proveedor, correo_proveedor, telefono_proveedor)
            VALUES (?, ?, ?);
            """;

    //actualizar nombre, correo y teléfono de un proveedor.
    // Parámetros: 1 = nombre_proveedor, 2 = correo_proveedor, 3 = telefono_proveedor, 4 = id_proveedor
    private static final String SQL_ACTUALIZAR = """
            UPDATE proveedores
            SET nombre_proveedor = ?, correo_proveedor = ?, telefono_proveedor = ?
            WHERE id_proveedor = ?;
            """;

    //eliminar un proveedor por su id (sin cascada).
    // Parámetros: 1 = id_proveedor
    // Si tiene compras, PostgreSQL rechaza el DELETE por llave foránea (23503).
    private static final String SQL_ELIMINAR = """
            DELETE FROM proveedores WHERE id_proveedor = ?;
            """;

    private final Connection conn;

    public ProveedorDao(Connection conn) {
        this.conn = conn;
    }

    public List<Proveedor> listar() throws SQLException {
        List<Proveedor> proveedores = new ArrayList<>();
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_LISTAR, "ProveedorDao.SQL_LISTAR");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                proveedores.add(mapear(rs));
            }
        }
        return proveedores;
    }

    public Optional<Proveedor> buscarPorId(int idProveedor) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_BUSCAR_POR_ID, "ProveedorDao.SQL_BUSCAR_POR_ID")) {
            ps.setInt(1, idProveedor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    /** correo o telefono puede ser null (pero no ambos). @return filas insertadas. */
    public int agregar(String nombre, String correo, String telefono) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_INSERTAR, "ProveedorDao.SQL_INSERTAR")) {
            ps.setString(1, nombre);
            JdbcUtil.setTextoOpcional(ps, 2, correo);
            JdbcUtil.setTextoOpcional(ps, 3, telefono);
            return ps.executeUpdate();
        }
    }

    /** @return filas actualizadas (0 si el id no existe). */
    public int actualizar(int idProveedor, String nombre, String correo, String telefono) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_ACTUALIZAR, "ProveedorDao.SQL_ACTUALIZAR")) {
            ps.setString(1, nombre);
            JdbcUtil.setTextoOpcional(ps, 2, correo);
            JdbcUtil.setTextoOpcional(ps, 3, telefono);
            ps.setInt(4, idProveedor);
            return ps.executeUpdate();
        }
    }

    /** @return filas eliminadas (0 si el id no existe). */
    public int eliminar(int idProveedor) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_ELIMINAR, "ProveedorDao.SQL_ELIMINAR")) {
            ps.setInt(1, idProveedor);
            return ps.executeUpdate();
        }
    }

    private static Proveedor mapear(ResultSet rs) throws SQLException {
        return new Proveedor(
                rs.getInt("id_proveedor"),
                rs.getString("nombre_proveedor"),
                rs.getString("correo_proveedor"),
                rs.getString("telefono_proveedor"));
    }
}
