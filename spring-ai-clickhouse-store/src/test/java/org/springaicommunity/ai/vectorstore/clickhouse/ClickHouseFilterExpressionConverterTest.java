package org.springaicommunity.ai.vectorstore.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

class ClickHouseFilterExpressionConverterTest {

    private final ClickHouseFilterExpressionConverter converter = new ClickHouseFilterExpressionConverter("metadata");
    private final FilterExpressionBuilder builder = new FilterExpressionBuilder();

    @Test
    void supportsNullChecksFromCurrentFilterDslAndParser() {
        assertThat(converter.convertExpression(builder.isNull("author").build()))
                .isEqualTo("metadata.author IS NULL");
        assertThat(converter.convertExpression(builder.isNotNull("author").build()))
                .isEqualTo("metadata.author IS NOT NULL");
        var parser = new FilterExpressionTextParser();
        assertThat(converter.convertExpression(parser.parse("author IS NULL"))).isEqualTo("metadata.author IS NULL");
        assertThat(converter.convertExpression(parser.parse("author IS NOT NULL")))
                .isEqualTo("metadata.author IS NOT NULL");
    }

    @Test
    void supportsLegacyNullChecks() {
        assertThat(converter.convertExpression(new Filter.Expression(
                        Filter.ExpressionType.EQ, new Filter.Key("author"), new Filter.Value(null))))
                .isEqualTo("metadata.author IS NULL");
        assertThat(converter.convertExpression(new Filter.Expression(
                        Filter.ExpressionType.NE, new Filter.Key("author"), new Filter.Value(null))))
                .isEqualTo("metadata.author IS NOT NULL");
    }

    @Test
    void supportsNegationAndValueRanges() {
        assertThat(converter.convertExpression(
                        builder.not(builder.in("year", 2024, 2025)).build()))
                .isEqualTo("metadata.year NOT IN (2024,2025)");
    }
}
