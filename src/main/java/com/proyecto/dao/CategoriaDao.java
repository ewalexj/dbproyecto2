package com.proyecto.dao;

import com.proyecto.modelo.Categoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a la tabla categorias.
 * nombre_categoria es UNIQUE y varchar(30): un duplicado da SQLSTATE 23505.
 */
public class CategoriaDao {

    //listar todas las categorías (ordenadas como prefieras).
    // Columnas que lee Java: id_categoria, nombre_categoria
    private static final String SQL_LISTAR = """
            SELECT id_categoria, nombre_categoria
            FROM categorias
            ORDER BY nombre_categoria;
            """;

    //insertar una categoría.
    // Parámetros: 1 = nombre_categoria
    private static final String SQL_INSERTAR = """
            INSERT INTO categorias (nombre_categoria) VALUES (?);
            """;

    //eliminar una categoría por su id (sin cascada).
    // Parámetros: 1 = id_categoria
    // Si tiene productos, PostgreSQL rechaza el DELETE por llave foránea (23503).
    private static final String SQL_ELIMINAR = """
            DELETE FROM categorias WHERE id_categoria = ?;
            """;

    private final Connection conn;

    public CategoriaDao(Connection conn) {
        this.conn = conn;
    }

    public List<Categoria> listar() throws SQLException {
        List<Categoria> categorias = new ArrayList<>();
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_LISTAR, "CategoriaDao.SQL_LISTAR");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categorias.add(new Categoria(
                        rs.getInt("id_categoria"),
                        rs.getString("nombre_categoria")));
            }
        }
        return categorias;
    }

    /** @return filas insertadas. */
    public int agregar(String nombreCategoria) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_INSERTAR, "CategoriaDao.SQL_INSERTAR")) {
            ps.setString(1, nombreCategoria);
            return ps.executeUpdate();
        }
    }

    /** @return filas eliminadas (0 si el id no existe). */
    public int eliminar(int idCategoria) throws SQLException {
        try (PreparedStatement ps = JdbcUtil.preparar(conn, SQL_ELIMINAR, "CategoriaDao.SQL_ELIMINAR")) {
            ps.setInt(1, idCategoria);
            return ps.executeUpdate();
        }
    }
}
