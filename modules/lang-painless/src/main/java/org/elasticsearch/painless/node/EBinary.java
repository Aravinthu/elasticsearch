/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.painless.node;

import org.elasticsearch.painless.AnalyzerCaster;
import org.elasticsearch.painless.DefBootstrap;
import org.elasticsearch.painless.Definition;
import org.elasticsearch.painless.Definition.Sort;
import org.elasticsearch.painless.Definition.Type;
import org.elasticsearch.painless.Globals;
import org.elasticsearch.painless.Locals;
import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.MethodWriter;
import org.elasticsearch.painless.Operation;
import org.elasticsearch.painless.WriterConstants;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a binary math expression.
 */
public final class EBinary extends AExpression {

    final Operation operation;
    private AExpression left;
    private AExpression right;

    private Type promote = null;                // promoted type
    private Type shiftDistance = null;          // for shifts, the rhs is promoted independently
    boolean cat = false;
    private boolean originallyExplicit = false; // record whether there was originally an explicit cast

    public EBinary(Location location, Operation operation, AExpression left, AExpression right) {
        super(location);

        this.operation = Objects.requireNonNull(operation);
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
    }

    @Override
    void extractVariables(Set<String> variables) {
        left.extractVariables(variables);
        right.extractVariables(variables);
    }

    @Override
    void analyze(Locals locals) {
        originallyExplicit = explicit;

        if (operation == Operation.MUL) {
            analyzeMul(locals);
        } else if (operation == Operation.DIV) {
            analyzeDiv(locals);
        } else if (operation == Operation.REM) {
            analyzeRem(locals);
        } else if (operation == Operation.ADD) {
            analyzeAdd(locals);
        } else if (operation == Operation.SUB) {
            analyzeSub(locals);
        } else if (operation == Operation.FIND) {
            analyzeRegexOp(locals);
        } else if (operation == Operation.MATCH) {
            analyzeRegexOp(locals);
        } else if (operation == Operation.LSH) {
            analyzeLSH(locals);
        } else if (operation == Operation.RSH) {
            analyzeRSH(locals);
        } else if (operation == Operation.USH) {
            analyzeUSH(locals);
        } else if (operation == Operation.BWAND) {
            analyzeBWAnd(locals);
        } else if (operation == Operation.XOR) {
            analyzeXor(locals);
        } else if (operation == Operation.BWOR) {
            analyzeBWOr(locals);
        } else {
            throw createError(new IllegalStateException("Illegal tree structure."));
        }
    }

    private void analyzeMul(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        promote = AnalyzerCaster.promoteNumeric(left.actual, right.actual, true);

        if (promote == null) {
            throw createError(new ClassCastException("Cannot apply multiply [*] to types " +
                "[" + left.actual.name + "] and [" + right.actual.name + "]."));
        }

        actual = promote;

        if (promote.sort == Sort.DEF) {
            left.expected = left.actual;
            right.expected = right.actual;
            if (expected != null) {
                actual = expected;
            }
        } else {
            left.expected = promote;
            right.expected = promote;
        }

        left = left.cast(variables);
        right = right.cast(variables);

        if (left.constant != null && right.constant != null) {
            Sort sort = promote.sort;

            if (sort == Sort.INT) {
                constant = (int)left.constant * (int)right.constant;
            } else if (sort == Sort.LONG) {
                constant = (long)left.constant * (long)right.constant;
            } else if (sort == Sort.FLOAT) {
                constant = (float)left.constant * (float)right.constant;
            } else if (sort == Sort.DOUBLE) {
                constant = (double)left.constant * (double)right.constant;
            } else {
                throw createError(new IllegalStateException("Illegal tree structure."));
            }
        }
    }

    private void analyzeDiv(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        promote = AnalyzerCaster.promoteNumeric(left.actual, right.actual, true);

        if (promote == null) {
            throw createError(new ClassCastException("Cannot apply divide [/] to types " +
                "[" + left.actual.name + "] and [" + right.actual.name + "]."));
        }

        actual = promote;

        if (promote.sort == Sort.DEF) {
            left.expected = left.actual;
            right.expected = right.actual;

            if (expected != null) {
                actual = expected;
            }
        } else {
            left.expected = promote;
            right.expected = promote;
        }

        left = left.cast(variables);
        right = right.cast(variables);

        if (left.constant != null && right.constant != null) {
            Sort sort = promote.sort;

            try {
                if (sort == Sort.INT) {
                    constant = (int)left.constant / (int)right.constant;
                } else if (sort == Sort.LONG) {
                    constant = (long)left.constant / (long)right.constant;
                } else if (sort == Sort.FLOAT) {
                    constant = (float)left.constant / (float)right.constant;
                } else if (sort == Sort.DOUBLE) {
                    constant = (double)left.constant / (double)right.constant;
                } else {
                    throw createError(new IllegalStateException("Illegal tree structure."));
                }
            } catch (ArithmeticException exception) {
                throw createError(exception);
            }
        }
    }

    private void analyzeRem(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        promote = AnalyzerCaster.promoteNumeric(left.actual, right.actual, true);

        if (promote == null) {
            throw createError(new ClassCastException("Cannot apply remainder [%] to types " +
                "[" + left.actual.name + "] and [" + right.actual.name + "]."));
        }

        actual = promote;

        if (promote.sort == Sort.DEF) {
            left.expected = left.actual;
            right.expected = right.actual;

            if (expected != null) {
                actual = expected;
            }
        } else {
            left.expected = promote;
            right.expected = promote;
        }

        left = left.cast(variables);
        right = right.cast(variables);

        if (left.constant != null && right.constant != null) {
            Sort sort = promote.sort;

            try {
                if (sort == Sort.INT) {
                    constant = (int)left.constant % (int)right.constant;
                } else if (sort == Sort.LONG) {
                    constant = (long)left.constant % (long)right.constant;
                } else if (sort == Sort.FLOAT) {
                    constant = (float)left.constant % (float)right.constant;
                } else if (sort == Sort.DOUBLE) {
                    constant = (double)left.constant % (double)right.constant;
                } else {
                    throw createError(new IllegalStateException("Illegal tree structure."));
                }
            } catch (ArithmeticException exception) {
                throw createError(exception);
            }
        }
    }

    private void analyzeAdd(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        promote = AnalyzerCaster.promoteAdd(left.actual, right.actual);

        if (promote == null) {
            throw createError(new ClassCastException("Cannot apply add [+] to types " +
                "[" + left.actual.name + "] and [" + right.actual.name + "]."));
        }

        Sort sort = promote.sort;

        actual = promote;

        if (sort == Sort.STRING) {
            left.expected = left.actual;

            if (left instanceof EBinary && ((EBinary)left).operation == Operation.ADD && left.actual.sort == Sort.STRING) {
                ((EBinary)left).cat = true;
            }

            right.expected = right.actual;

            if (right instanceof EBinary && ((EBinary)right).operation == Operation.ADD && right.actual.sort == Sort.STRING) {
                ((EBinary)right).cat = true;
            }
        } else if (sort == Sort.DEF) {
            left.expected = left.actual;
            right.expected = right.actual;

            if (expected != null) {
                actual = expected;
            }
        } else {
            left.expected = promote;
            right.expected = promote;
        }

        left = left.cast(variables);
        right = right.cast(variables);

        if (left.constant != null && right.constant != null) {
            if (sort == Sort.INT) {
                constant = (int)left.constant + (int)right.constant;
            } else if (sort == Sort.LONG) {
                constant = (long)left.constant + (long)right.constant;
            } else if (sort == Sort.FLOAT) {
                constant = (float)left.constant + (float)right.constant;
            } else if (sort == Sort.DOUBLE) {
                constant = (double)left.constant + (double)right.constant;
            } else if (sort == Sort.STRING) {
                constant = "" + left.constant + right.constant;
            } else {
                throw createError(new IllegalStateException("Illegal tree structure."));
            }
        }

    }

    private void analyzeSub(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        promote = AnalyzerCaster.promoteNumeric(left.actual, right.actual, true);

        if (promote == null) {
            throw createError(new ClassCastException("Cannot apply subtract [-] to types " +
                "[" + left.actual.name + "] and [" + right.actual.name + "]."));
        }

        actual = promote;

        if (promote.sort == Sort.DEF) {
            left.expected = left.actual;
            right.expected = right.actual;

            if (expected != null) {
                actual = expected;
            }
        } else {
            left.expected = promote;
            right.expected = promote;
        }

        left = left.cast(variables);
        right = right.cast(variables);

        if (left.constant != null && right.constant != null) {
            Sort sort = promote.sort;

            if (sort == Sort.INT) {
                constant = (int)left.constant - (int)right.constant;
            } else if (sort == Sort.LONG) {
                constant = (long)left.constant - (long)right.constant;
            } else if (sort == Sort.FLOAT) {
                constant = (float)left.constant - (float)right.constant;
            } else if (sort == Sort.DOUBLE) {
                constant = (double)left.constant - (double)right.constant;
            } else {
                throw createError(new IllegalStateException("Illegal tree structure."));
            }
        }
    }

    private void analyzeRegexOp(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        left.expected = Definition.STRING_TYPE;
        right.expected = Definition.PATTERN_TYPE;

        left = left.cast(variables);
        right = right.cast(variables);

        promote = Definition.BOOLEAN_TYPE;
        actual = Definition.BOOLEAN_TYPE;
    }

    private void analyzeLSH(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        Type lhspromote = AnalyzerCaster.promoteNumeric(left.actual, false);
        Type rhspromote = AnalyzerCaster.promoteNumeric(right.actual, false);

        if (lhspromote == null || rhspromote == null) {
            throw createError(new ClassCastException("Cannot apply left shift [<<] to types " +
                "[" + left.actual.name + "] and [" + right.actual.name + "]."));
        }

        actual = promote = lhspromote;
        shiftDistance = rhspromote;

        if (lhspromote.sort == Sort.DEF || rhspromote.sort == Sort.DEF) {
            left.expected = left.actual;
            right.expected = right.actual;

            if (expected != null) {
                actual = expected;
            }
        } else {
            left.expected = lhspromote;

            if (rhspromote.sort == Sort.LONG) {
                right.expected = Definition.INT_TYPE;
                right.explicit = true;
            } else {
                right.expected = rhspromote;
            }
        }

        left = left.cast(variables);
        right = right.cast(variables);

        if (left.constant != null && right.constant != null) {
            Sort sort = lhspromote.sort;

            if (sort == Sort.INT) {
                constant = (int)left.constant << (int)right.constant;
            } else if (sort == Sort.LONG) {
                constant = (long)left.constant << (int)right.constant;
            } else {
                throw createError(new IllegalStateException("Illegal tree structure."));
            }
        }
    }

    private void analyzeRSH(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        Type lhspromote = AnalyzerCaster.promoteNumeric(left.actual, false);
        Type rhspromote = AnalyzerCaster.promoteNumeric(right.actual, false);

        if (lhspromote == null || rhspromote == null) {
            throw createError(new ClassCastException("Cannot apply right shift [>>] to types " +
                "[" + left.actual.name + "] and [" + right.actual.name + "]."));
        }

        actual = promote = lhspromote;
        shiftDistance = rhspromote;

        if (lhspromote.sort == Sort.DEF || rhspromote.sort == Sort.DEF) {
            left.expected = left.actual;
            right.expected = right.actual;

            if (expected != null) {
                actual = expected;
            }
        } else {
            left.expected = lhspromote;

            if (rhspromote.sort == Sort.LONG) {
                right.expected = Definition.INT_TYPE;
                right.explicit = true;
            } else {
                right.expected = rhspromote;
            }
        }

        left = left.cast(variables);
        right = right.cast(variables);

        if (left.constant != null && right.constant != null) {
            Sort sort = lhspromote.sort;

            if (sort == Sort.INT) {
                constant = (int)left.constant >> (int)right.constant;
            } else if (sort == Sort.LONG) {
                constant = (long)left.constant >> (int)right.constant;
            } else {
                throw createError(new IllegalStateException("Illegal tree structure."));
            }
        }
    }

    private void analyzeUSH(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        Type lhspromote = AnalyzerCaster.promoteNumeric(left.actual, false);
        Type rhspromote = AnalyzerCaster.promoteNumeric(right.actual, false);

        actual = promote = lhspromote;
        shiftDistance = rhspromote;

        if (lhspromote == null || rhspromote == null) {
            throw createError(new ClassCastException("Cannot apply unsigned shift [>>>] to types " +
                "[" + left.actual.name + "] and [" + right.actual.name + "]."));
        }

        if (lhspromote.sort == Sort.DEF || rhspromote.sort == Sort.DEF) {
            left.expected = left.actual;
            right.expected = right.actual;

            if (expected != null) {
                actual = expected;
            }
        } else {
            left.expected = lhspromote;

            if (rhspromote.sort == Sort.LONG) {
                right.expected = Definition.INT_TYPE;
                right.explicit = true;
            } else {
                right.expected = rhspromote;
            }
        }

        left = left.cast(variables);
        right = right.cast(variables);

        if (left.constant != null && right.constant != null) {
            Sort sort = lhspromote.sort;

            if (sort == Sort.INT) {
                constant = (int)left.constant >>> (int)right.constant;
            } else if (sort == Sort.LONG) {
                constant = (long)left.constant >>> (int)right.constant;
            } else {
                throw createError(new IllegalStateException("Illegal tree structure."));
            }
        }
    }

    private void analyzeBWAnd(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        promote = AnalyzerCaster.promoteNumeric(left.actual, right.actual, false);

        if (promote == null) {
            throw createError(new ClassCastException("Cannot apply and [&] to types " +
                "[" + left.actual.name + "] and [" + right.actual.name + "]."));
        }

        actual = promote;

        if (promote.sort == Sort.DEF) {
            left.expected = left.actual;
            right.expected = right.actual;

            if (expected != null) {
                actual = expected;
            }
        } else {
            left.expected = promote;
            right.expected = promote;
        }

        left = left.cast(variables);
        right = right.cast(variables);

        if (left.constant != null && right.constant != null) {
            Sort sort = promote.sort;

            if (sort == Sort.INT) {
                constant = (int)left.constant & (int)right.constant;
            } else if (sort == Sort.LONG) {
                constant = (long)left.constant & (long)right.constant;
            } else {
                throw createError(new IllegalStateException("Illegal tree structure."));
            }
        }
    }

    private void analyzeXor(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        promote = AnalyzerCaster.promoteXor(left.actual, right.actual);

        if (promote == null) {
            throw createError(new ClassCastException("Cannot apply xor [^] to types " +
                "[" + left.actual.name + "] and [" + right.actual.name + "]."));
        }

        actual = promote;

        if (promote.sort == Sort.DEF) {
            left.expected = left.actual;
            right.expected = right.actual;
            if (expected != null) {
                actual = expected;
            }
        } else {
            left.expected = promote;
            right.expected = promote;
        }

        left = left.cast(variables);
        right = right.cast(variables);

        if (left.constant != null && right.constant != null) {
            Sort sort = promote.sort;

            if (sort == Sort.BOOL) {
                constant = (boolean)left.constant ^ (boolean)right.constant;
            } else if (sort == Sort.INT) {
                constant = (int)left.constant ^ (int)right.constant;
            } else if (sort == Sort.LONG) {
                constant = (long)left.constant ^ (long)right.constant;
            } else {
                throw createError(new IllegalStateException("Illegal tree structure."));
            }
        }
    }

    private void analyzeBWOr(Locals variables) {
        left.analyze(variables);
        right.analyze(variables);

        promote = AnalyzerCaster.promoteNumeric(left.actual, right.actual, false);

        if (promote == null) {
            throw createError(new ClassCastException("Cannot apply or [|] to types " +
                "[" + left.actual.name + "] and [" + right.actual.name + "]."));
        }

        actual = promote;

        if (promote.sort == Sort.DEF) {
            left.expected = left.actual;
            right.expected = right.actual;
            if (expected != null) {
                actual = expected;
            }
        } else {
            left.expected = promote;
            right.expected = promote;
        }

        left = left.cast(variables);
        right = right.cast(variables);

        if (left.constant != null && right.constant != null) {
            Sort sort = promote.sort;

            if (sort == Sort.INT) {
                constant = (int)left.constant | (int)right.constant;
            } else if (sort == Sort.LONG) {
                constant = (long)left.constant | (long)right.constant;
            } else {
                throw createError(new IllegalStateException("Illegal tree structure."));
            }
        }
    }

    @Override
    void write(MethodWriter writer, Globals globals) {
        writer.writeDebugInfo(location);

        if (promote.sort == Sort.STRING && operation == Operation.ADD) {
            if (!cat) {
                writer.writeNewStrings();
            }

            left.write(writer, globals);

            if (!(left instanceof EBinary) || !((EBinary)left).cat) {
                writer.writeAppendStrings(left.actual);
            }

            right.write(writer, globals);

            if (!(right instanceof EBinary) || !((EBinary)right).cat) {
                writer.writeAppendStrings(right.actual);
            }

            if (!cat) {
                writer.writeToStrings();
            }
        } else if (operation == Operation.FIND || operation == Operation.MATCH) {
            right.write(writer, globals);
            left.write(writer, globals);
            writer.invokeVirtual(Definition.PATTERN_TYPE.type, WriterConstants.PATTERN_MATCHER);

            if (operation == Operation.FIND) {
                writer.invokeVirtual(Definition.MATCHER_TYPE.type, WriterConstants.MATCHER_FIND);
            } else if (operation == Operation.MATCH) {
                writer.invokeVirtual(Definition.MATCHER_TYPE.type, WriterConstants.MATCHER_MATCHES);
            } else {
                throw new IllegalStateException("Illegal tree structure.");
            }
        } else {
            left.write(writer, globals);
            right.write(writer, globals);

            if (promote.sort == Sort.DEF || (shiftDistance != null && shiftDistance.sort == Sort.DEF)) {
                // def calls adopt the wanted return value. if there was a narrowing cast,
                // we need to flag that so that its done at runtime.
                int flags = 0;
                if (originallyExplicit) {
                    flags |= DefBootstrap.OPERATOR_EXPLICIT_CAST;
                }
                writer.writeDynamicBinaryInstruction(location, actual, left.actual, right.actual, operation, flags);
            } else {
                writer.writeBinaryInstruction(location, actual, operation);
            }
        }
    }
}
