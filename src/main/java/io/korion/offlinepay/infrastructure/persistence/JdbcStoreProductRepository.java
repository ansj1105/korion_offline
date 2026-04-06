package io.korion.offlinepay.infrastructure.persistence;

import io.korion.offlinepay.application.port.StoreProductRepository;
import io.korion.offlinepay.domain.model.StoreProduct;
import io.korion.offlinepay.infrastructure.persistence.mapper.StoreProductRowMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStoreProductRepository implements StoreProductRepository {

    private final JdbcClient jdbcClient;
    private final StoreProductRowMapper storeProductRowMapper;

    public JdbcStoreProductRepository(
            JdbcClient jdbcClient,
            StoreProductRowMapper storeProductRowMapper
    ) {
        this.jdbcClient = jdbcClient;
        this.storeProductRowMapper = storeProductRowMapper;
    }

    @Override
    public List<StoreProduct> findByUserId(long userId) {
        String sql = QueryBuilder
                .select(
                        "store_products",
                        "id",
                        "user_id",
                        "name",
                        "description",
                        "image_url",
                        "price",
                        "stock_current",
                        "stock_total",
                        "visible",
                        "sort_order",
                        "created_at",
                        "updated_at"
                )
                .where("user_id", QueryBuilder.Op.EQ, ":userId")
                .orderBy("sort_order ASC")
                .orderBy("id DESC")
                .build();
        return jdbcClient.sql(sql)
                .param("userId", userId)
                .query(storeProductRowMapper)
                .list();
    }

    @Override
    public Optional<StoreProduct> findByUserIdAndId(long userId, long productId) {
        String sql = QueryBuilder
                .select(
                        "store_products",
                        "id",
                        "user_id",
                        "name",
                        "description",
                        "image_url",
                        "price",
                        "stock_current",
                        "stock_total",
                        "visible",
                        "sort_order",
                        "created_at",
                        "updated_at"
                )
                .where("user_id", QueryBuilder.Op.EQ, ":userId")
                .where("id", QueryBuilder.Op.EQ, ":productId")
                .build();
        return jdbcClient.sql(sql)
                .param("userId", userId)
                .param("productId", productId)
                .query(storeProductRowMapper)
                .optional();
    }

    @Override
    public int nextSortOrder(long userId) {
        Integer value = jdbcClient.sql("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM store_products WHERE user_id = :userId")
                .param("userId", userId)
                .query(Integer.class)
                .single();
        return value == null ? 1 : value;
    }

    @Override
    public StoreProduct save(
            long userId,
            String name,
            String description,
            String imageUrl,
            BigDecimal price,
            int stockCurrent,
            int stockTotal,
            boolean visible,
            int sortOrder
    ) {
        Long productId = jdbcClient.sql("""
                INSERT INTO store_products (
                    user_id,
                    name,
                    description,
                    image_url,
                    price,
                    stock_current,
                    stock_total,
                    visible,
                    sort_order
                ) VALUES (
                    :userId,
                    :name,
                    :description,
                    :imageUrl,
                    :price,
                    :stockCurrent,
                    :stockTotal,
                    :visible,
                    :sortOrder
                )
                RETURNING id
                """)
                .param("userId", userId)
                .param("name", name)
                .param("description", description)
                .param("imageUrl", imageUrl)
                .param("price", price)
                .param("stockCurrent", stockCurrent)
                .param("stockTotal", stockTotal)
                .param("visible", visible)
                .param("sortOrder", sortOrder)
                .query(Long.class)
                .single();
        return findByUserIdAndId(userId, productId == null ? 0L : productId).orElseThrow();
    }

    @Override
    public StoreProduct update(
            long userId,
            long productId,
            String name,
            String description,
            String imageUrl,
            BigDecimal price,
            int stockCurrent,
            int stockTotal,
            boolean visible
    ) {
        String sql = QueryBuilder.update("store_products")
                .set("name", ":name")
                .set("description", ":description")
                .set("image_url", ":imageUrl")
                .set("price", ":price")
                .set("stock_current", ":stockCurrent")
                .set("stock_total", ":stockTotal")
                .set("visible", ":visible")
                .touchUpdatedAt()
                .where("user_id", QueryBuilder.Op.EQ, ":userId")
                .where("id", QueryBuilder.Op.EQ, ":productId")
                .build();
        jdbcClient.sql(sql)
                .param("userId", userId)
                .param("productId", productId)
                .param("name", name)
                .param("description", description)
                .param("imageUrl", imageUrl)
                .param("price", price)
                .param("stockCurrent", stockCurrent)
                .param("stockTotal", stockTotal)
                .param("visible", visible)
                .update();
        return findByUserIdAndId(userId, productId).orElseThrow();
    }

    @Override
    public void delete(long userId, long productId) {
        jdbcClient.sql("DELETE FROM store_products WHERE user_id = :userId AND id = :productId")
                .param("userId", userId)
                .param("productId", productId)
                .update();
    }

    @Override
    public void updateSortOrder(long userId, long productId, int sortOrder) {
        String sql = QueryBuilder.update("store_products")
                .set("sort_order", ":sortOrder")
                .touchUpdatedAt()
                .where("user_id", QueryBuilder.Op.EQ, ":userId")
                .where("id", QueryBuilder.Op.EQ, ":productId")
                .build();
        jdbcClient.sql(sql)
                .param("userId", userId)
                .param("productId", productId)
                .param("sortOrder", sortOrder)
                .update();
    }
}
