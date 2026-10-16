package io.github.penguinedavid.comprs.common.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parses a boolean expression, builds its truth table, and reduces it to a
 * minimum sum-of-products form using Quine-McCluskey.
 *
 * Supported syntax:
 *   variables : single identifiers like A, B, x1 (letters/digits, must start with a letter)
 *   NOT       : ! or ~ prefix, or ' postfix        e.g. !A, ~A, A'
 *   AND       : & or * or juxtaposition (AB means A AND B)
 *   XOR       : ^
 *   OR        : | or +
 *   grouping  : ( )
 *
 * Operator precedence (highest to lowest): NOT, AND, XOR, OR.
 *
 * The exact minimum prime-implicant cover is selected. If that SOP is longer
 * than the user's original expression, the original expression is retained:
 * SOP is not always the shortest notation when XOR or factoring is available.
 */
public final class BooleanAlgebra {

    private BooleanAlgebra() {
    }

    /** Thrown for any malformed input: bad syntax, unknown characters, unmatched parens, too many variables. */
    public static class BooleanParseException extends RuntimeException {
        public BooleanParseException(String message) {
            super(message);
        }
    }

    private static final int MAX_VARIABLES = 8;

    /**
     * The full truth table for an expression: the variables in sorted order,
     * and for each row (in the standard order where the last variable toggles
     * fastest) the assignment used and the result of evaluating the expression.
     */
    public record TruthTable(List<String> variables, List<boolean[]> assignments, List<Boolean> results) {
    }

    /**
     * Parses expression and evaluates it for every possible assignment of its
     * variables, i.e. builds its full truth table. Used directly by ,truth and
     * internally by {@link #simplify}.
     */
    public static TruthTable evaluateTruthTable(String expression) {
        Node root = new Parser(expression).parseFull();

        Set<String> variableSet = new TreeSet<>();
        root.collectVariables(variableSet);
        List<String> variables = new ArrayList<>(variableSet);

        if (variables.isEmpty()) {
            throw new BooleanParseException("Expression contains no variables");
        }
        if (variables.size() > MAX_VARIABLES) {
            throw new BooleanParseException(
                "Too many variables (" + variables.size() + "), limit is " + MAX_VARIABLES);
        }

        int n = variables.size();
        List<boolean[]> assignments = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();
        for (int mask = 0; mask < (1 << n); mask++) {
            boolean[] assignment = new boolean[n];
            for (int i = 0; i < n; i++) {
                assignment[i] = ((mask >> (n - 1 - i)) & 1) == 1;
            }
            assignments.add(assignment);
            results.add(root.evaluate(variables, assignment));
        }

        return new TruthTable(variables, assignments, results);
    }

    /**
     * Parses, evaluates the truth table, and returns the shorter of the input
     * and its minimum sum-of-products form, such as "(A & B) | (!C)".
     * Returns "1" for a tautology and "0" for a contradiction.
     */
    public static String simplify(String expression) {
        TruthTable table = evaluateTruthTable(expression);
        List<String> variables = table.variables();
        int n = variables.size();

        List<Integer> minterms = new ArrayList<>();
        for (int mask = 0; mask < table.results().size(); mask++) {
            if (table.results().get(mask)) {
                minterms.add(mask);
            }
        }

        if (minterms.isEmpty()) {
            return "0";
        }
        if (minterms.size() == (1 << n)) {
            return "1";
        }

        List<String> primeImplicants = QuineMcCluskey.findPrimeImplicants(minterms, n);
        List<String> cover = QuineMcCluskey.selectCover(primeImplicants, minterms);

        List<String> terms = new ArrayList<>();
        for (String pi : cover) {
            terms.add(formatTerm(pi, variables));
        }
        String sumOfProducts = String.join(" | ", terms);
        String original = expression.replaceAll("\\s+", "");
        return sumOfProducts.replaceAll("\\s+", "").length() < original.length()
            ? sumOfProducts
            : original;
    }

    /** Turns a Quine-McCluskey bit pattern (with '-' as don't-care) into "(A & !B)" style text. */
    private static String formatTerm(String bits, List<String> variables) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < bits.length(); i++) {
            char c = bits.charAt(i);
            if (c == '-') {
                continue;
            }
            if (!first) {
                sb.append(" & ");
            }
            first = false;
            if (c == '0') {
                sb.append('!');
            }
            sb.append(variables.get(i));
        }
        if (sb.length() == 0) {
            return "1";
        }
        return "(" + sb + ")";
    }

    // ---------------------------------------------------------------------
    // AST
    // ---------------------------------------------------------------------

    private interface Node {
        boolean evaluate(List<String> variables, boolean[] assignment);

        void collectVariables(Set<String> out);
    }

    private static final class VarNode implements Node {
        final String name;

        VarNode(String name) {
            this.name = name;
        }

        @Override
        public boolean evaluate(List<String> variables, boolean[] assignment) {
            int idx = variables.indexOf(name);
            return assignment[idx];
        }

        @Override
        public void collectVariables(Set<String> out) {
            out.add(name);
        }
    }

    /** A literal 0 (false) or 1 (true); contributes no variables to the truth table. */
    private static final class ConstNode implements Node {
        final boolean value;

        ConstNode(boolean value) {
            this.value = value;
        }

        @Override
        public boolean evaluate(List<String> variables, boolean[] assignment) {
            return value;
        }

        @Override
        public void collectVariables(Set<String> out) {
            // no variable to contribute
        }
    }

    private static final class NotNode implements Node {
        final Node child;

        NotNode(Node child) {
            this.child = child;
        }

        @Override
        public boolean evaluate(List<String> variables, boolean[] assignment) {
            return !child.evaluate(variables, assignment);
        }

        @Override
        public void collectVariables(Set<String> out) {
            child.collectVariables(out);
        }
    }

    private enum Op { AND, XOR, OR }

    private static final class BinaryNode implements Node {
        final Op op;
        final Node left;
        final Node right;

        BinaryNode(Op op, Node left, Node right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean evaluate(List<String> variables, boolean[] assignment) {
            boolean a = left.evaluate(variables, assignment);
            boolean b = right.evaluate(variables, assignment);
            return switch (op) {
                case AND -> a && b;
                case XOR -> a ^ b;
                case OR -> a || b;
            };
        }

        @Override
        public void collectVariables(Set<String> out) {
            left.collectVariables(out);
            right.collectVariables(out);
        }
    }

    // ---------------------------------------------------------------------
    // Recursive-descent parser
    // ---------------------------------------------------------------------

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            // Strip whitespace up front; layout has no meaning in this grammar.
            this.src = src.replaceAll("\\s+", "");
            this.pos = 0;
        }

        Node parseFull() {
            if (src.isEmpty()) {
                throw new BooleanParseException("Empty expression");
            }
            Node result = parseOr();
            if (pos != src.length()) {
                throw new BooleanParseException("Unexpected character '" + src.charAt(pos) + "' at position " + pos);
            }
            return result;
        }

        // OR has the lowest precedence: A | B + C
        private Node parseOr() {
            Node left = parseXor();
            while (peekIsOr()) {
                consumeOr();
                Node right = parseXor();
                left = new BinaryNode(Op.OR, left, right);
            }
            return left;
        }

        private Node parseXor() {
            Node left = parseAnd();
            while (peek() == '^') {
                pos++;
                Node right = parseAnd();
                left = new BinaryNode(Op.XOR, left, right);
            }
            return left;
        }

        // AND also fires on juxtaposition: "AB" parses as A & B.
        private Node parseAnd() {
            Node left = parseNot();
            while (true) {
                if (peekIsAnd()) {
                    consumeAnd();
                    Node right = parseNot();
                    left = new BinaryNode(Op.AND, left, right);
                } else if (canStartOperand() && !peekIsOr() && peek() != '^' && peek() != ')') {
                    // implicit AND via juxtaposition, e.g. "AB" or "A(B|C)"
                    Node right = parseNot();
                    left = new BinaryNode(Op.AND, left, right);
                } else {
                    break;
                }
            }
            return left;
        }

        private Node parseNot() {
            if (peek() == '!' || peek() == '~') {
                pos++;
                return new NotNode(parseNot());
            }
            Node node = parsePrimary();
            // postfix NOT, e.g. A'
            while (peek() == '\'') {
                pos++;
                node = new NotNode(node);
            }
            return node;
        }

        private Node parsePrimary() {
            char c = peek();
            if (c == '(') {
                pos++;
                Node inner = parseOr();
                expect(')');
                return inner;
            }
            if (Character.isLetter(c)) {
                // A variable is exactly one letter plus any trailing digits (A, x1, B12).
                // Deliberately NOT multiple letters: "AB" must tokenize as A then B so
                // juxtaposition (implicit AND) works, matching textbook boolean notation.
                int start = pos;
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
                return new VarNode(src.substring(start, pos));
            }
            if (c == '0' || c == '1') {
                // Boolean literal: 1 is always true, 0 is always false, regardless of
                // variable assignment. This does NOT consume trailing digits, so "10"
                // is the literal "1" immediately juxtaposed with the literal "0" (1 & 0),
                // not a two-digit number, since booleans only have two values.
                pos++;
                return new ConstNode(c == '1');
            }
            throw new BooleanParseException(
                atEnd() ? "Unexpected end of expression" : "Unexpected character '" + c + "' at position " + pos);
        }

        private boolean peekIsOr() {
            return peek() == '|' || peek() == '+';
        }

        private void consumeOr() {
            pos++;
        }

        private boolean peekIsAnd() {
            return peek() == '&' || peek() == '*';
        }

        private void consumeAnd() {
            pos++;
        }

        private boolean canStartOperand() {
            char c = peek();
            return c == '(' || c == '!' || c == '~' || c == '0' || c == '1' || Character.isLetter(c);
        }

        private char peek() {
            return atEnd() ? '\0' : src.charAt(pos);
        }

        private boolean atEnd() {
            return pos >= src.length();
        }

        private void expect(char c) {
            if (peek() != c) {
                throw new BooleanParseException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }
    }

    // ---------------------------------------------------------------------
    // Quine-McCluskey minimization
    // ---------------------------------------------------------------------

    private static final class QuineMcCluskey {

        /** Combines minterms pairwise by bit-difference until no more combinations are possible. */
        static List<String> findPrimeImplicants(List<Integer> minterms, int bitCount) {
            Set<String> currentGroup = new LinkedHashSet<>();
            for (int m : minterms) {
                currentGroup.add(toBits(m, bitCount));
            }

            Set<String> primeImplicants = new LinkedHashSet<>();
            Set<String> current = currentGroup;

            while (!current.isEmpty()) {
                Set<String> next = new LinkedHashSet<>();
                Set<String> combined = new LinkedHashSet<>();

                List<String> list = new ArrayList<>(current);
                for (int i = 0; i < list.size(); i++) {
                    for (int j = i + 1; j < list.size(); j++) {
                        String merged = tryCombine(list.get(i), list.get(j));
                        if (merged != null) {
                            next.add(merged);
                            combined.add(list.get(i));
                            combined.add(list.get(j));
                        }
                    }
                }

                for (String term : list) {
                    if (!combined.contains(term)) {
                        primeImplicants.add(term);
                    }
                }

                current = next;
            }

            return new ArrayList<>(primeImplicants);
        }

        /** Combines two bit patterns if they differ in exactly one position; that position becomes '-'. */
        private static String tryCombine(String a, String b) {
            if (a.length() != b.length()) {
                return null;
            }
            int diffPos = -1;
            for (int i = 0; i < a.length(); i++) {
                if (a.charAt(i) != b.charAt(i)) {
                    if (diffPos != -1) {
                        return null;
                    }
                    diffPos = i;
                } else if (a.charAt(i) == '-' && b.charAt(i) != '-') {
                    return null; // don't-care positions must line up to combine
                }
            }
            if (diffPos == -1) {
                return null;
            }
            StringBuilder sb = new StringBuilder(a);
            sb.setCharAt(diffPos, '-');
            return sb.toString();
        }

        /** Finds a minimum prime-implicant cover, minimizing term count then literal count. */
        static List<String> selectCover(List<String> primeImplicants, List<Integer> minterms) {
            CoverSearch search = new CoverSearch(primeImplicants);
            search.find(new LinkedHashSet<>(minterms), new ArrayList<>());
            if (search.bestCover == null) {
                throw new IllegalStateException("Could not cover all minterms with prime implicants");
            }
            return search.bestCover;
        }

        private static final class CoverSearch {
            private final List<String> primeImplicants;
            private List<String> bestCover;
            private int bestLiteralCount = Integer.MAX_VALUE;

            private CoverSearch(List<String> primeImplicants) {
                this.primeImplicants = primeImplicants;
            }

            private void find(Set<Integer> uncovered, List<String> selected) {
                if (uncovered.isEmpty()) {
                    int literalCount = selected.stream().mapToInt(QuineMcCluskey::literalCount).sum();
                    if (bestCover == null || selected.size() < bestCover.size()
                        || (selected.size() == bestCover.size() && literalCount < bestLiteralCount)) {
                        bestCover = List.copyOf(selected);
                        bestLiteralCount = literalCount;
                    }
                    return;
                }
                if (bestCover != null && selected.size() >= bestCover.size()) {
                    return;
                }

                List<String> branchCandidates = null;
                for (int minterm : uncovered) {
                    List<String> candidates = new ArrayList<>();
                    for (String pi : primeImplicants) {
                        if (!selected.contains(pi) && covers(pi, minterm)) {
                            candidates.add(pi);
                        }
                    }
                    if (branchCandidates == null || candidates.size() < branchCandidates.size()) {
                        branchCandidates = candidates;
                        if (candidates.size() <= 1) {
                            break;
                        }
                    }
                }

                if (branchCandidates == null || branchCandidates.isEmpty()) {
                    return;
                }
                for (String pi : branchCandidates) {
                    Set<Integer> remaining = new LinkedHashSet<>(uncovered);
                    remaining.removeIf(minterm -> covers(pi, minterm));
                    selected.add(pi);
                    find(remaining, selected);
                    selected.remove(selected.size() - 1);
                }
            }
        }

        private static int literalCount(String pattern) {
            int count = 0;
            for (int i = 0; i < pattern.length(); i++) {
                if (pattern.charAt(i) != '-') {
                    count++;
                }
            }
            return count;
        }

        private static boolean covers(String pattern, int minterm) {
            String bits = toBits(minterm, pattern.length());
            for (int i = 0; i < pattern.length(); i++) {
                if (pattern.charAt(i) != '-' && pattern.charAt(i) != bits.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        private static String toBits(int value, int bitCount) {
            StringBuilder sb = new StringBuilder();
            for (int i = bitCount - 1; i >= 0; i--) {
                sb.append((value >> i) & 1);
            }
            return sb.toString();
        }
    }
}