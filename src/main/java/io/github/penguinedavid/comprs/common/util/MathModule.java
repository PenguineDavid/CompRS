package io.github.penguinedavid.comprs.common.util;

import java.util.Stack;

public class MathModule {
    public static boolean validParentheses(String str) {
        Stack<Character> stack = new Stack<>();
        
        for (char c : str.toCharArray()) {
            if (c == '(') {
                stack.push(c);
            } else if (c == ')') {
                if (stack.empty()) {
                    return false;
                }
                stack.pop();
            }
        }
        
        return stack.empty();
    }

    public static double evaluate(String expression) {
        // Normalize to lowercase to handle constants and functions case-insensitively.
        expression = expression.replaceAll("\\s+", "").toLowerCase();
        
        Stack<Double> values = new Stack<>();
        Stack<String> operators = new Stack<>();
        
        int i = 0;
        while (i < expression.length()) {
            char c = expression.charAt(i);
            
            // 1. Parse Numbers (including a unary minus/plus sign)
            if (Character.isDigit(c) || c == '.'
                || ((c == '-' || c == '+') && isUnaryPosition(expression, i))) {
                StringBuilder sb = new StringBuilder();
                if (c == '-') {
                    sb.append('-');
                    i++;
                } else if (c == '+') {
                    i++; 
                }
                while (i < expression.length() && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                    sb.append(expression.charAt(i++));
                }
                values.push(Double.parseDouble(sb.toString()));
                continue; 
            }
            
            // Parse named mathematical constants.
            else if (expression.startsWith("tau", i)) {
                values.push(2 * Math.PI);
                i += 3;
                continue;
            } else if (expression.startsWith("phi", i)) {
                values.push((1 + Math.sqrt(5)) / 2);
                i += 3;
                continue;
            } else if (expression.startsWith("pi", i)) {
                values.push(Math.PI);
                i += 2;
                continue;
            } else if (c == 'e') {
                values.push(Math.E);
                i++;
                continue;
            }
            
            // Parse named functions, including mod when used in function form.
            else if (Character.isLetter(c) && (!expression.startsWith("mod", i)
                || expression.startsWith("mod(", i))) {
                StringBuilder funcName = new StringBuilder();
                while (i < expression.length() && Character.isLetter(expression.charAt(i))) {
                    funcName.append(expression.charAt(i++));
                }
                String name = funcName.toString();
                operators.push(name.equals("mod") ? "modfn" : name);
                continue; 
            }
            
            // 4. Parse Opening Parenthesis
            else if (c == '(') {
                operators.push("(");
            }
            
            // 5. Parse Closing Parenthesis / Process Functions
            else if (c == ')') {
                while (!operators.isEmpty() && !operators.peek().equals("(")) {
                    values.push(applyOperator(operators.pop(), values));
                }
                if (operators.isEmpty()) {
                    throw new IllegalArgumentException("Mismatched parentheses");
                }
                operators.pop(); // Remove '('
                
                if (!operators.isEmpty() && isFunction(operators.peek())) {
                    values.push(applyOperator(operators.pop(), values));
                }
            }
            
            // 6. Parse Commas (Function Argument Separators)
            else if (c == ',') {
                while (!operators.isEmpty() && !operators.peek().equals("(")) {
                    values.push(applyOperator(operators.pop(), values));
                }
            }
            
            // 7. Parse Mathematical Operators (+, -, *, /, mod)
            else {
                String op = String.valueOf(c);
                if (expression.startsWith("mod", i)) {
                    op = "mod";
                    i += 2; 
                }
                
                while (!operators.isEmpty() && hasPrecedence(op, operators.peek())) {
                    values.push(applyOperator(operators.pop(), values));
                }
                operators.push(op);
            }
            i++;
        }
        
        while (!operators.isEmpty()) {
            String op = operators.peek();
            if (op.equals("(")) {
                throw new IllegalArgumentException("Mismatched parentheses");
            }
            values.push(applyOperator(operators.pop(), values));
        }
        
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Invalid expression");
        }
        
        return values.pop();
    }

    private static boolean isUnaryPosition(String expression, int i) {
        if (i == 0) {
            return true;
        }
        char prev = expression.charAt(i - 1);
        return prev == '(' || prev == ',' || prev == '+' || prev == '-' || prev == '*' || prev == '/' || prev == '^';
    }

    private static boolean isFunction(String name) {
        return name.equals("pow") || name.equals("sqrt") || name.equals("abs") 
            || name.equals("log") || name.equals("sin") || name.equals("cos") || name.equals("tan")
            || name.equals("modfn");
    }

    private static boolean hasPrecedence(String op1, String op2) {
        if (op2.equals("(") || op2.equals(")")) return false;
        if (isFunction(op2)) return true; 
        if ((op1.equals("*") || op1.equals("/") || op1.equals("mod")) && (op2.equals("+") || op2.equals("-"))) return false;
        return true;
    }

    private static double applyOperator(String op, Stack<Double> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Missing operand for operator/Internal parsing error: " + op);
        }

        // Handle single-argument functions
        if (op.equals("sqrt") || op.equals("abs") || op.equals("log") 
         || op.equals("sin") || op.equals("cos") || op.equals("tan")) {
            double a = values.pop();
            switch (op) {
                case "sqrt": return Math.sqrt(a);
                case "abs":  return Math.abs(a);
                case "log":  return Math.log10(a);
                case "sin":  return Math.sin(Math.toRadians(a)); 
                case "cos":  return Math.cos(Math.toRadians(a));
                case "tan":  return Math.tan(Math.toRadians(a));
            }
        }

        // Handle binary operators and two-argument functions
        if (values.size() < 2) {
            throw new IllegalArgumentException("Missing operand for operator: " + op);
        }
        double b = values.pop();
        double a = values.pop();
        
        switch (op) {
            case "+": return a + b;
            case "-": return a - b;
            case "*": return a * b;
            case "/": 
                if (b == 0) throw new UnsupportedOperationException("Cannot divide by zero");
                return a / b;
            case "mod":
            case "modfn": return a % b;
            case "pow": return Math.pow(a, b);
            default:
                throw new IllegalArgumentException("Unknown function or operator: " + op);
        }
    }
}