package org.zkoss.zss.model.sys.formula.Decomposer;

import org.zkoss.poi.ss.formula.ptg.AddPtg;
import org.zkoss.poi.ss.formula.ptg.MultiplyPtg;
import org.zkoss.poi.ss.formula.ptg.Ptg;
import org.zkoss.poi.ss.formula.ptg.RefVariablePtg;
import org.zkoss.zss.model.sys.formula.Exception.OptimizationError;
import org.zkoss.zss.model.sys.formula.Primitives.SingleAggregateOperator;
import org.zkoss.zss.model.sys.formula.Primitives.BinaryFunction;
import org.zkoss.zss.model.sys.formula.Primitives.GroupedTransformOperator;
import org.zkoss.zss.model.sys.formula.Primitives.LogicalOperator;

import java.util.Arrays;

import static org.zkoss.zss.model.sys.formula.Decomposer.TransformDecomposer.*;
import static org.zkoss.zss.model.sys.formula.Primitives.FilterOperator.buildSingleFilter;
import static org.zkoss.zss.model.sys.formula.Primitives.LogicalOperator.connect;

public abstract class FunctionDecomposer {

    static final int COUNT = 0;
    static final int ISNA = 2;
    static final int ISERROR = 3;
    static final int SUM = 4;
    static final int AVERAGE = 5;
    static final int MIN = 6;
    static final int MAX = 7;
    static final int ROWFUNC = 8; // ROW
    static final int COLUMN = 9;
    static final int NA = 10;
    static final int NPV = 11;
    static final int STDEV = 12;
    static final int DOLLAR = 13;
    static final int SIN = 15;
    static final int COS = 16;
    static final int TAN = 17;
    static final int ATAN = 18;
    static final int PI = 19;
    static final int SQRT = 20;
    static final int EXP = 21;
    static final int LN = 22;
    static final int ABS = 24;
    static final int INT = 25;
    static final int SIGN = 26;
    static final int ROUND = 27;
    static final int LOOKUP = 28;
    static final int INDEX = 29;
    static final int MID = 31;
    static final int LEN = 32;
    static final int VALUE = 33;
    static final int TRUE = 34;
    static final int FALSE = 35;
    static final int AND = 36;
    static final int OR = 37;
    static final int NOT = 38;
    static final int MOD = 39;
    static final int VAR = 46;
    static final int TEXT = 48;
    static final int PV = 56;
    static final int FV = 57;
    static final int NPER = 58;
    static final int PMT = 59;
    static final int RATE = 60;
    static final int IRR = 62;
    static final int RAND = 63;
    static final int MATCH = 64;
    static final int TIMEFUNC = 66;
    static final int DAY = 67;
    static final int MONTH = 68;
    static final int YEAR = 69;
    static final int HOUR = 71;
    static final int MINUTE = 72;
    static final int SECOND = 73;
    static final int NOW = 74;
    static final int ROWS = 76;
    static final int COLUMNS = 77;
    static final int SEARCH = 82;
    static final int ASIN = 98;
    static final int ACOS = 99;
    static final int HLOOKUP = 101;
    static final int VLOOKUP = 102;
    static final int ISREF = 105;
    static final int LOG = 109;
    static final int CHAR = 111;
    static final int LOWER = 112;
    static final int UPPER = 113;
    static final int LEFT = 115;
    static final int RIGHT = 116;
    static final int EXACT = 117;
    static final int TRIM = 118;
    static final int REPLACE = 119;
    static final int SUBSTITUTE = 120;
    static final int FIND = 124;
    static final int ISTEXT = 127;
    static final int ISNUMBER = 128;
    static final int ISBLANK = 129;
    static final int T = 130;
    static final int CLEAN = 162;  //Aniket Banerjee
    static final int COUNTA = 169;
    static final int PRODUCT = 183;
    static final int FACT = 184;
    static final int ISNONTEXT = 190;
    static final int VARP = 194;
    static final int TRUNC = 197;
    static final int ISLOGICAL = 198;
    static final int ROUNDUP = 212;
    static final int ROUNDDOWN = 213;
    static final int RANK = 216;
    static final int ADDRESS = 219;  //Aniket Banerjee
    static final int DAYS360 = 220;
    static final int TODAY = 221;
    static final int MEDIAN = 227;
    static final int SUMPRODUCT = 228;
    static final int SINH = 229;
    static final int COSH = 230;
    static final int TANH = 231;
    static final int ASINH = 232;
    static final int ACOSH = 233;
    static final int ATANH = 234;
    static final int ERRORTYPE = 261;
    static final int AVEDEV = 269;
    static final int COMBIN = 276;
    static final int EVEN = 279;
    static final int FLOOR = 285;
    static final int CEILING = 288;
    static final int ODD = 298;
    static final int POISSON = 300;
    static final int SUMXMY2 = 303;
    static final int SUMX2MY2 = 304;
    static final int SUMX2PY2 = 305;
    static final int DEVSQ = 318;
    static final int SUMSQ = 321;
    static final int LARGE = 325;
    static final int SMALL = 326;
    static final int MODE = 330;
    static final int CONCATENATE = 336;
    static final int POWER = 337;
    static final int RADIANS = 342;
    static final int DEGREES = 343;
    static final int SUBTOTAL = 344;
    static final int SUMIF = 345;
    static final int COUNTIF = 346;
    static final int COUNTBLANK = 347;
    static final int HYPERLINK = 359;
    static final int MAXA = 362;
    static final int MINA = 363;
    static final int UNION = 368;
    static final int DIFFERENCE = 369;
    static final int INTERSECTION = 370;
    static final int CROSSPRODUCT = 371;
    static final int SELECT = 372;
    static final int PROJECT = 373;
    static final int RENAME = 374;
    static final int JOIN = 375;
    static final int MULTIPLEAREA = 376;
    static final int SQLFUNCTION = 377;

    static   FunctionDecomposer[] produceLogicalOperatorDictionary() {
        FunctionDecomposer[] funcDict = new FunctionDecomposer[378];

        funcDict[SUM] = new AggregateDecomposer(BinaryFunction.PLUS, AddPtg.instance);

        funcDict[SUMPRODUCT] = new FunctionDecomposer() {
            @Override
            public LogicalOperator decompose(LogicalOperator[] ops) throws OptimizationError {
                Ptg[] ptgs = new Ptg[ops.length * 2 - 1];
                ptgs[0] = new RefVariablePtg(0);
                for (int i = 1; i < ops.length;i++){
                    ptgs[2 * i - 1] = new RefVariablePtg(i);
                    ptgs[2 * i] = MultiplyPtg.nonOperatorInstance;
                }
                GroupedTransformOperator transform = new GroupedTransformOperator(ops,ptgs);
                LogicalOperator aggregate = new SingleAggregateOperator(BinaryFunction.PLUS);
                connect(transform,aggregate);
                return aggregate;
            }
        };

        FunctionDecomposer SUMSQAURE = new FunctionDecomposer() {
            @Override
            public LogicalOperator decompose(LogicalOperator[] ops) throws OptimizationError {
                return funcDict[SUM].decompose(
                        Arrays.stream(ops)
                        .map(op->funcDict[SUMPRODUCT].decompose(new LogicalOperator[]{op,op}))
                        .toArray(LogicalOperator[]::new));
            }
        };

        funcDict[COUNT] = new AggregateDecomposer(BinaryFunction.COUNTPLUS, AddPtg.instance);

        funcDict[AVERAGE] = divide(funcDict[SUM],funcDict[COUNT]);



        funcDict[STDEV] = sqrt(divide(
                subtract(SUMSQAURE,multiply(funcDict[SUM],funcDict[AVERAGE])),
                subtract(funcDict[COUNT],ONE)));
        // TODO : CHECK IF IT IS N-1

        funcDict[COUNTIF] = new FunctionDecomposer() {
            @Override
            public LogicalOperator decompose(LogicalOperator[] ops) throws OptimizationError {
                return funcDict[COUNT].decompose(new LogicalOperator[]{
                        buildSingleFilter(new LogicalOperator[]{ops[1],ops[0]})});
            }
        };

        return funcDict;
    }

    public abstract LogicalOperator decompose(LogicalOperator[] ops) throws OptimizationError;
}