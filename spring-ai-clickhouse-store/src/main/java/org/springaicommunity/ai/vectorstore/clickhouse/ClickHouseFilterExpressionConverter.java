package org.springaicommunity.ai.vectorstore.clickhouse;

import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

/**
 * Converts {@link Filter.Expression} into Clickhouse JSON metadata filter expression format.
 *
 * @author Linar Abzaltdinov
 */
public class ClickHouseFilterExpressionConverter extends AbstractFilterExpressionConverter {
    private String metadataColumnName;

    public ClickHouseFilterExpressionConverter(String metadataColumnName) {
        this.metadataColumnName = metadataColumnName;
    }

    @Override
    protected void doExpression(Filter.Expression expression, StringBuilder context) {
        this.convertOperand(expression.left(), context);
        if ((EQ.equals(expression.type()) || NE.equals(expression.type()))
                && expression.right() instanceof Filter.Value rightExpressionValue
                && rightExpressionValue.value() == null) {
            // if the right operand is null, we need to use the IS (NOT) NULL operator
            if (EQ.equals(expression.type())) {
                context.append(" IS NULL");
            } else if (NE.equals(expression.type())) {
                context.append(" IS NOT NULL");
            }
        } else {
            context.append(getOperationSymbol(expression));
            this.convertOperand(expression.right(), context);
        }
    }

    @Override
    protected void doSingleValue(Object value, StringBuilder context) {
        if (value instanceof String) {
            context.append(String.format("\'%s\'", value));
        } else {
            context.append(value);
        }
    }

    private String getOperationSymbol(Filter.Expression exp) {
        switch (exp.type()) {
            case AND:
                return " AND ";
            case OR:
                return " OR ";
            case EQ:
                return " == ";
            case NE:
                return " != ";
            case LT:
                return " < ";
            case LTE:
                return " <= ";
            case GT:
                return " > ";
            case GTE:
                return " >= ";
            case IN:
                return " IN ";
            case NIN:
                return " NOT IN ";
            case NOT:
                return " NOT ";
            default:
                throw new RuntimeException("Not supported expression type: " + exp.type());
        }
    }

    @Override
    protected void doKey(Filter.Key key, StringBuilder context) {
        context.append(metadataColumnName).append(".").append(key.key());
    }

    @Override
    protected void doStartGroup(Filter.Group group, StringBuilder context) {
        context.append("(");
    }

    @Override
    protected void doEndGroup(Filter.Group group, StringBuilder context) {
        context.append(")");
    }

    @Override
    protected void doStartValueRange(Filter.Value listValue, StringBuilder context) {
        context.append("(");
    }

    @Override
    protected void doEndValueRange(Filter.Value listValue, StringBuilder context) {
        context.append(")");
    }
}
