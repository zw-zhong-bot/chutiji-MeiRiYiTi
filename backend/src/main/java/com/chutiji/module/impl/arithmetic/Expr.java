package com.chutiji.module.impl.arithmetic;

/** 表达式树节点：叶子是整数，内部节点是四则运算。 */
public sealed interface Expr permits Expr.Num, Expr.Bin {

    Fraction evaluate();

    String render();

    record Num(long value) implements Expr {
        @Override
        public Fraction evaluate() {
            return Fraction.of(value);
        }

        @Override
        public String render() {
            return Long.toString(value);
        }
    }

    record Bin(Op op, Expr left, Expr right) implements Expr {
        @Override
        public Fraction evaluate() {
            Fraction l = left.evaluate();
            Fraction r = right.evaluate();
            return switch (op) {
                case ADD -> l.add(r);
                case SUB -> l.subtract(r);
                case MUL -> l.multiply(r);
                case DIV -> l.divide(r);
            };
        }

        @Override
        public String render() {
            String l = wrap(left, false);
            String r = wrap(right, true);
            return l + " " + op.symbol() + " " + r;
        }

        /** 子节点优先级低于父节点时必须加括号；同优先级时只有右子节点需要（左结合）。 */
        private String wrap(Expr child, boolean isRight) {
            String text = child.render();
            if (!(child instanceof Bin bin)) {
                return text;
            }
            boolean needParen = bin.op().precedence() < op.precedence()
                    || (bin.op().precedence() == op.precedence()
                        && isRight
                        && (op == Op.SUB || op == Op.DIV));
            return needParen ? "(" + text + ")" : text;
        }
    }

    enum Op {
        ADD("+", 1), SUB("-", 1), MUL("×", 2), DIV("÷", 2);

        private final String symbol;
        private final int precedence;

        Op(String symbol, int precedence) {
            this.symbol = symbol;
            this.precedence = precedence;
        }

        public String symbol() {
            return symbol;
        }

        public int precedence() {
            return precedence;
        }
    }
}
