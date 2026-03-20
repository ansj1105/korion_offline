package io.korion.offlinepay.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class QueryBuilder {

    public enum Op {
        EQ("="),
        NE("!="),
        GT(">"),
        GTE(">="),
        LT("<"),
        LTE("<="),
        LIKE("LIKE"),
        IN("IN");

        private final String symbol;

        Op(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    private QueryBuilder() {
    }

    public static SelectBuilder select(String table, String... columns) {
        return new SelectBuilder(table, columns);
    }

    public static InsertBuilder insert(String table, String... columns) {
        return new InsertBuilder(table, columns);
    }

    public static UpdateBuilder update(String table) {
        return new UpdateBuilder(table);
    }

    public static final class SelectBuilder {
        private final String table;
        private final List<String> columns = new ArrayList<>();
        private final List<String> conditions = new ArrayList<>();
        private final List<String> groupBy = new ArrayList<>();
        private final List<String> orderBy = new ArrayList<>();
        private Integer limit;

        private SelectBuilder(String table, String... columns) {
            this.table = table;
            if (columns == null || columns.length == 0) {
                this.columns.add("*");
            } else {
                this.columns.addAll(List.of(columns));
            }
        }

        public SelectBuilder where(String condition) {
            conditions.add(condition);
            return this;
        }

        public SelectBuilder where(String column, Op op, String valueExpression) {
            conditions.add(column + " " + op.symbol() + " " + valueExpression);
            return this;
        }

        public SelectBuilder orderBy(String clause) {
            orderBy.add(clause);
            return this;
        }

        public SelectBuilder groupBy(String clause) {
            groupBy.add(clause);
            return this;
        }

        public SelectBuilder limit(int value) {
            this.limit = value;
            return this;
        }

        public String build() {
            StringBuilder sql = new StringBuilder("SELECT ")
                    .append(String.join(", ", columns))
                    .append(" FROM ")
                    .append(table);
            if (!conditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", conditions));
            }
            if (!groupBy.isEmpty()) {
                sql.append(" GROUP BY ").append(String.join(", ", groupBy));
            }
            if (!orderBy.isEmpty()) {
                sql.append(" ORDER BY ").append(String.join(", ", orderBy));
            }
            if (limit != null) {
                sql.append(" LIMIT ").append(limit);
            }
            return sql.toString();
        }
    }

    public static final class InsertBuilder {
        private final String table;
        private final List<String> columns = new ArrayList<>();

        private InsertBuilder(String table, String... columns) {
            this.table = table;
            this.columns.addAll(List.of(columns));
        }

        public String build() {
            StringJoiner columnJoiner = new StringJoiner(", ");
            StringJoiner valueJoiner = new StringJoiner(", ");
            for (String column : columns) {
                columnJoiner.add(column);
                valueJoiner.add(":" + column);
            }
            return "INSERT INTO " + table + " (" + columnJoiner + ") VALUES (" + valueJoiner + ")";
        }
    }

    public static final class UpdateBuilder {
        private final String table;
        private final List<String> sets = new ArrayList<>();
        private final List<String> conditions = new ArrayList<>();

        private UpdateBuilder(String table) {
            this.table = table;
        }

        public UpdateBuilder set(String expression) {
            sets.add(expression);
            return this;
        }

        public UpdateBuilder touchUpdatedAt() {
            sets.add("updated_at = NOW()");
            return this;
        }

        public UpdateBuilder where(String condition) {
            conditions.add(condition);
            return this;
        }

        public UpdateBuilder where(String column, Op op, String valueExpression) {
            conditions.add(column + " " + op.symbol() + " " + valueExpression);
            return this;
        }

        public String build() {
            StringBuilder sql = new StringBuilder("UPDATE ")
                    .append(table)
                    .append(" SET ")
                    .append(String.join(", ", sets));
            if (!conditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", conditions));
            }
            return sql.toString();
        }
    }
}
