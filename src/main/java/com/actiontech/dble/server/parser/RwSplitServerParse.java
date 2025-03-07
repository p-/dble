/*
 * Copyright (C) 2016-2022 ActionTech.
 * based on code by MyCATCopyrightHolder Copyright (c) 2013, OpenCloudDB/MyCAT.
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher.
 */
package com.actiontech.dble.server.parser;

import com.actiontech.dble.config.Versions;
import com.actiontech.dble.route.parser.util.ParseUtil;

import java.util.regex.Matcher;

/**
 * @author mycat
 */
public final class RwSplitServerParse extends AbstractServerParse {
    protected RwSplitServerParse() {
        super();
    }

    @Override
    public int parse(String stmt) {
        int length = stmt.length();
        //FIX BUG FOR SQL SUCH AS /XXXX/SQL
        int rt = OTHER;
        for (int i = 0; i < length; ++i) {
            switch (stmt.charAt(i)) {
                case ' ':
                case '\t':
                case '\r':
                case '\n':
                    continue;
                case '/':
                    // such as /*!40101 SET character_set_client = @saved_cs_client
                    // */;
                    if (i == 0 && stmt.charAt(1) == '*' && stmt.charAt(2) == '!' && stmt.charAt(length - 2) == '*' &&
                            stmt.charAt(length - 1) == '/') {
                        return MYSQL_CMD_COMMENT;
                    }
                    //fall through
                case '#':
                    i = ParseUtil.comment(stmt, i);
                    if (i + 1 == length) {
                        return MYSQL_COMMENT;
                    }
                    continue;
                case '-':
                    i = ParseUtil.commentDoubleDash(stmt, i);
                    if (i + 1 == length) {
                        return MYSQL_COMMENT;
                    }
                    continue;
                case 'A':
                case 'a':
                    rt = aCheck(stmt, i);
                    break;
                case 'B':
                case 'b':
                    rt = beginCheck(stmt, i);
                    break;
                case 'C':
                case 'c':
                    rt = cCheck(stmt, i);
                    break;
                case 'D':
                case 'd':
                    rt = deleteOrdCheck(stmt, i);
                    break;
                case 'E':
                case 'e':
                    rt = eCheck(stmt, i);
                    break;
                case 'G':
                case 'g':
                    rt = gCheck(stmt, i);
                    break;
                case 'I':
                case 'i':
                    rt = iCheck(stmt, i);
                    break;
                case 'M':
                case 'm':
                    rt = migrateCheck(stmt, i);
                    break;
                case 'O':
                case 'o':
                    rt = optimizeCheck(stmt, i);
                    break;
                case 'P':
                case 'p':
                    rt = prepareCheck(stmt, i);
                    break;
                case 'R':
                case 'r':
                    rt = rCheck(stmt, i);
                    break;
                case 'S':
                case 's':
                    rt = sCheck(stmt, i);
                    break;
                case 'T':
                case 't':
                    rt = tCheck(stmt, i);
                    break;
                case 'U':
                case 'u':
                    rt = uCheck(stmt, i);
                    break;
                case 'K':
                case 'k':
                    rt = killCheck(stmt, i);
                    break;
                case 'H':
                case 'h':
                    rt = helpCheck(stmt, i);
                    break;
                case 'L':
                case 'l':
                    rt = lCheck(stmt, i);
                    break;
                case 'X':
                case 'x':
                    rt = xCheck(stmt, i);
                    break;
                default:
                    break;
            }
            break;
        }
        return rt;
    }

    private int xCheck(String stmt, int offset) {
        if (stmt.length() > offset + 1) {
            switch (stmt.charAt(++offset)) {
                case 'A':
                case 'a':
                    char c = stmt.charAt(++offset);
                    if (c == ' ' || c == '\t' || c == '\r' || c == '\n')
                        return xaCheck(stmt, offset);
                    break;
                default:
                    break;
            }
        }
        return OTHER;
    }

    private int xaCheck(String stmt, int offset) {
        char c1 = stmt.charAt(++offset);
        if (((c1 == 'S' || c1 == 's') || (c1 == 'B' || c1 == 'b')) &&
                stmt.length() > offset + 5 &&
                (stmt.substring(offset + 1, offset + 5).equalsIgnoreCase("TART") ||
                        stmt.substring(offset + 1, offset + 5).equalsIgnoreCase("EGIN"))) {
            offset += 5;
            return (offset << 8) | XA_START;
        } else if ((c1 == 'E' || c1 == 'e') &&
                stmt.length() > offset + 3 &&
                stmt.substring(offset + 1, offset + 3).equalsIgnoreCase("ND")) {
            offset += 3;
            return (offset << 8) | XA_END;
        } else if ((c1 == 'P' || c1 == 'p') &&
                stmt.length() > offset + 7 &&
                stmt.substring(offset + 1, offset + 7).equalsIgnoreCase("REPARE")) {
            offset += 7;
            return (offset << 8) | XA_PREPARE;
        } else if ((c1 == 'R' || c1 == 'r') &&
                stmt.length() > offset + 8 &&
                stmt.substring(offset + 1, offset + 8).equalsIgnoreCase("OLLBACK")) {
            offset += 8;
            return (offset << 8) | XA_ROLLBACK;
        } else if ((c1 == 'C' || c1 == 'c') &&
                stmt.length() > offset + 6 &&
                stmt.substring(offset + 1, offset + 6).equalsIgnoreCase("OMMIT")) {
            offset += 6;
            return (offset << 8) | XA_COMMIT;
        } else {
            return OTHER;
        }
    }

    //create TEMPORARY TABLE XXXX
    private int createTempTableCheck(String stmt, int offset) {
        String keyword = "EMPORARY";
        if (!ParseUtil.compare(stmt, offset, keyword)) {
            return DDL;
        }
        offset += keyword.length();
        offset = ParseUtil.skipSpace(stmt, offset);
        keyword = "TABLE";
        if (!ParseUtil.compare(stmt, offset, keyword)) {
            return DDL;
        }
        offset += keyword.length();
        offset = ParseUtil.skipSpace(stmt, offset);
        return (offset << 8) | CREATE_TEMPORARY_TABLE;
    }

    //DROP [TEMPORARY] TABLE [IF EXISTS]
    // tbl_name [, tbl_name] ...
    // [RESTRICT | CASCADE]
    private int dropTableCheck(String stmt, int offset) {
        String keyword = "TEMPORARY";
        if (ParseUtil.compare(stmt, offset, keyword)) {
            offset += keyword.length();
            offset = ParseUtil.skipSpace(stmt, offset);
        }
        keyword = "TABLE";
        if (!ParseUtil.compare(stmt, offset, keyword)) {
            return DDL;
        }
        offset += keyword.length();
        offset = ParseUtil.skipSpace(stmt, offset);
        return (offset << 8) | DROP_TABLE;
    }


    // INSERT' ' | INSTALL '  '
    protected int iCheck(String stmt, int offset) {
        int type = OTHER;
        if (stmt.length() > offset + 2) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            if ((c1 == 'N' || c1 == 'n') && (c2 == 'S' || c2 == 's')) {
                switch (c3) {
                    case 'E':
                    case 'e':
                        type = inseCheck(stmt, offset);
                        break;
                    case 'T':
                    case 't':
                        type = instCheck(stmt, offset);
                        break;
                    default:
                        break;
                }
            }
        }
        return type;
    }

    private int instCheck(String stmt, int offset) {
        if (stmt.length() > offset + 3) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'A' || c1 == 'a') && (c2 == 'L' || c2 == 'l') && (c3 == 'L' || c3 == 'l') && ParseUtil.isSpace(c4)) {
                return INSTALL;
            }
        }
        return OTHER;
    }

    private int inseCheck(String stmt, int offset) {
        if (stmt.length() > offset + 2) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            if ((c1 == 'R' || c1 == 'r') && (c2 == 'T' || c2 == 't') && ParseUtil.isSpace(c3)) {
                return INSERT;
            }
        }
        return OTHER;
    }


    //grant
    private int gCheck(String stmt, int offset) {
        if (stmt.length() > offset + 4) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            if ((c1 == 'R' || c1 == 'r') &&
                    (c2 == 'A' || c2 == 'a') &&
                    (c3 == 'N' || c3 == 'n') &&
                    (c4 == 'T' || c4 == 't') && ParseUtil.isSpace(c5)) {
                return GRANT;
            }
        }
        return OTHER;
    }


    protected int rCheck(String stmt, int offset) {
        if (stmt.length() > ++offset) {
            switch (stmt.charAt(offset)) {
                case 'E':
                case 'e':
                    return reCheck(stmt, offset);
                case 'O':
                case 'o':
                    return rollbackCheck(stmt, offset);
                default:
                    return OTHER;
            }
        }
        return OTHER;
    }

    private int reCheck(String stmt, int offset) {
        if (stmt.length() > ++offset) {
            switch (stmt.charAt(offset)) {
                case 'N':
                case 'n':
                    return rename(stmt, offset);
                case 'P':
                case 'p':
                    return repCheck(stmt, offset);
                case 'l':
                case 'L':
                    return release(stmt, offset);
                case 'v':
                case 'V':
                    return revoke(stmt, offset);
                default:
                    return OTHER;
            }
        }
        return OTHER;
    }


    //revoke
    private int revoke(String stmt, int offset) {
        if (stmt.length() > offset + 5) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'o' || c1 == 'O') && (c2 == 'k' || c2 == 'K') && (c3 == 'e' || c3 == 'E') && ParseUtil.isSpace(c4)) {
                return REVOKE;
            }
        }
        return OTHER;
    }


    protected int rename(String stmt, int offset) {
        if (stmt.length() > offset + 4) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'A' || c1 == 'a') && (c2 == 'M' || c2 == 'm') && (c3 == 'E' || c3 == 'e') &&
                    (c4 == ' ' || c4 == '\t' || c4 == '\r' || c4 == '\n')) {
                return RENAME;
            }
        }
        return OTHER;
    }

    //UNLOCK | UNINSTALL
    private int unCheck(String stmt, int offset) {
        int type = OTHER;
        switch (stmt.charAt(++offset)) {
            case 'L':
            case 'l':
                type = unlCheck(stmt, offset);
                break;
            case 'I':
            case 'i':
                type = uniCheck(stmt, offset);
                break;
            default:
                break;
        }
        return type;
    }

    private int uniCheck(String stmt, int offset) {
        if (stmt.length() > offset + 6) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            char c7 = stmt.charAt(++offset);
            if ((c1 == 'N' || c1 == 'n') &&
                    (c2 == 'S' || c2 == 's') &&
                    (c3 == 'T' || c3 == 't') &&
                    (c4 == 'A' || c4 == 'a') &&
                    (c5 == 'L' || c5 == 'l') &&
                    (c6 == 'L' || c6 == 'l') &&
                    ParseUtil.isSpace(c7)) {
                return UNINSTALL;
            }
        }
        return OTHER;
    }

    private int unlCheck(String stmt, int offset) {
        if (stmt.length() > offset + 3) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'O' || c1 == 'o') &&
                    (c2 == 'C' || c2 == 'c') &&
                    (c3 == 'K' || c3 == 'k') &&
                    ParseUtil.isSpace(c4)) {
                return UNLOCK;
            }
        }
        return OTHER;
    }

    // UPDATE' ' | USE' '
    protected int uCheck(String stmt, int offset) {
        if (stmt.length() > ++offset) {
            switch (stmt.charAt(offset)) {
                case 'P':
                case 'p':
                    if (stmt.length() > offset + 5) {
                        char c1 = stmt.charAt(++offset);
                        char c2 = stmt.charAt(++offset);
                        char c3 = stmt.charAt(++offset);
                        char c4 = stmt.charAt(++offset);
                        char c5 = stmt.charAt(++offset);
                        if ((c1 == 'D' || c1 == 'd') &&
                                (c2 == 'A' || c2 == 'a') &&
                                (c3 == 'T' || c3 == 't') &&
                                (c4 == 'E' || c4 == 'e') &&
                                (c5 == ' ' || c5 == '\t' || c5 == '\r' || c5 == '\n')) {
                            return UPDATE;
                        }
                    }
                    break;
                case 'S':
                case 's':
                    if (stmt.length() > offset + 2) {
                        char c1 = stmt.charAt(++offset);
                        char c2 = stmt.charAt(++offset);
                        if ((c1 == 'E' || c1 == 'e') &&
                                (c2 == ' ' || c2 == '\t' || c2 == '\r' || c2 == '\n')) {
                            return (offset << 8) | USE;
                        }
                    }
                    break;
                case 'N':
                case 'n':
                    return unCheck(stmt, offset);
                default:
                    return OTHER;
            }
        }
        return OTHER;
    }

    protected int sCheck(String stmt, int offset) {
        if (stmt.length() > ++offset) {
            switch (stmt.charAt(offset)) {
                case 'A':
                case 'a':
                    return savepointCheck(stmt, offset);
                case 'E':
                case 'e':
                    return seCheck(stmt, offset);
                case 'H':
                case 'h':
                    return showCheck(stmt, offset);
                case 'T':
                case 't':
                    return startCheck(stmt, offset);
                default:
                    return OTHER;
            }
        }
        return OTHER;
    }

    // START' '
    private int startCheck(String stmt, int offset) {
        if (stmt.length() > offset + 4) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'A' || c1 == 'a') && (c2 == 'R' || c2 == 'r') &&
                    (c3 == 'T' || c3 == 't') &&
                    (c4 == ' ' || c4 == '\t' || c4 == '\r' || c4 == '\n')) {
                return (new RwSplitServerParseStart()).parse(stmt, offset);
            }
        }
        return OTHER;
    }


    protected int eCheck(String stmt, int offset) {
        int sqlType = OTHER;
        if (stmt.length() > offset + 1) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            if (c1 == 'X' || c1 == 'x') {
                switch (c2) {
                    case 'E':
                    case 'e':
                        sqlType = executeCheck(stmt, offset);
                        break;
                    case 'P':
                    case 'p':
                        sqlType = explainCheck(stmt, offset);
                        break;
                    default:
                        break;
                }
            }
        }
        return sqlType;
    }

    protected int flushCheck(String stmt, int offset) {
        if (stmt.length() > offset + 5) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'L' || c1 == 'l') && (c2 == 'U' || c2 == 'u') &&
                    (c3 == 'S' || c3 == 's') && (c4 == 'H' || c4 == 'h') &&
                    ParseUtil.isSpace(stmt.charAt(++offset))) {
                return FLUSH;
            }
        }
        return OTHER;
    }

    protected int lCheck(String stmt, int offset) {
        if (stmt.length() > offset + 3) {
            char c1 = stmt.charAt(++offset);
            if (c1 == 'o' || c1 == 'O') {
                switch (stmt.charAt(++offset)) {
                    case 'A':
                    case 'a':
                        return loadCheck(stmt, offset);
                    case 'C':
                    case 'c':
                        return lockCheck(stmt, offset);
                    default:
                        return OTHER;
                }
            }
        }

        return OTHER;
    }

    private int loadCheck(String stmt, int offset) {
        if (stmt.length() > offset + 2) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            if ((c1 == 'D' || c1 == 'd') && (c2 == ' ' || c2 == '\t' || c2 == '\r' || c2 == '\n')) {
                return loadParse(stmt, offset);
            }
        }
        return OTHER;
    }

    private int loadParse(String stmt, int offset) {
        if (stmt.length() > offset + 4) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            if ((c1 == 'D' || c1 == 'd') && (c2 == 'A' || c2 == 'a') && (c3 == 'T' || c3 == 't') && (c4 == 'A' || c4 == 'a') &&
                    (c5 == ' ' || c5 == '\t' || c5 == '\r' || c5 == '\n')) {
                return LOAD_DATA_INFILE_SQL;
            } else if ((c1 == 'I' || c1 == 'i') && (c2 == 'N' || c2 == 'n') && (c3 == 'D' || c3 == 'd') && (c4 == 'E' || c4 == 'e') &&
                    (c5 == 'X' || c5 == 'x')) {
                if (stmt.length() > offset + 1) {
                    char c6 = stmt.charAt(++offset);
                    if ((c6 == ' ' || c6 == '\t' || c6 == '\r' || c6 == '\n')) {
                        return UNSUPPORT;
                    }
                }
            }
        }
        return OTHER;
    }

    private int lockCheck(String stmt, int offset) {
        if (stmt.length() > offset + 2) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            if ((c1 == 'K' || c1 == 'k') && (c2 == ' ' || c2 == '\t' || c2 == '\r' || c2 == '\n')) {
                return LOCK;
            }
        }
        return OTHER;
    }

    protected int migrateCheck(String stmt, int offset) {
        if (stmt.length() > offset + 7) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);

            if ((c1 == 'i' || c1 == 'I') &&
                    (c2 == 'g' || c2 == 'G') &&
                    (c3 == 'r' || c3 == 'R') &&
                    (c4 == 'a' || c4 == 'A') &&
                    (c5 == 't' || c5 == 'T') &&
                    (c6 == 'e' || c6 == 'E')) {
                return MIGRATE;
            }
        }
        return OTHER;
    }

    protected int optimizeCheck(String stmt, int offset) {
        if (stmt.length() > offset + 7) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            char c7 = stmt.charAt(++offset);
            char c8 = stmt.charAt(++offset);
            if ((c1 == 'P' || c1 == 'p') && (c2 == 'T' || c2 == 't') && (c3 == 'I' || c3 == 'i') && (c4 == 'M' || c4 == 'm') &&
                    (c5 == 'I' || c5 == 'i') && (c6 == 'Z' || c6 == 'z') && (c7 == 'E' || c7 == 'e') &&
                    (c8 == ' ' || c8 == '\t' || c8 == '\r' || c8 == '\n')) {
                return UNSUPPORT;
            }
        }
        return OTHER;
    }

    protected int prepareCheck(String stmt, int offset) {
        if (isPrepare(stmt, offset)) return SCRIPT_PREPARE;
        return OTHER;
    }

    private boolean isPrepare(String stmt, int offset) {
        if (stmt.length() > offset + 6) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            char c7 = stmt.charAt(++offset);
            if ((c1 == 'R' || c1 == 'r') && (c2 == 'E' || c2 == 'e') && (c3 == 'P' || c3 == 'p') && (c4 == 'A' || c4 == 'a') &&
                    (c5 == 'R' || c5 == 'r') && (c6 == 'E' || c6 == 'e') &&
                    (c7 == ' ' || c7 == '\t' || c7 == '\r' || c7 == '\n')) {
                return true;
            }
        }
        return false;
    }

    //truncate
    protected int tCheck(String stmt, int offset) {
        if (stmt.length() > offset + 7) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            char c7 = stmt.charAt(++offset);
            char c8 = stmt.charAt(++offset);

            if ((c1 == 'R' || c1 == 'r') && (c2 == 'U' || c2 == 'u') && (c3 == 'N' || c3 == 'n') && (c4 == 'C' || c4 == 'c') &&
                    (c5 == 'A' || c5 == 'a') && (c6 == 'T' || c6 == 't') && (c7 == 'E' || c7 == 'e') &&
                    (c8 == ' ' || c8 == '\t' || c8 == '\r' || c8 == '\n')) {
                return DDL;
            }
        }
        return OTHER;
    }

    //alter table/view/... and analyze
    protected int aCheck(String stmt, int offset) {
        if (stmt.length() > offset + 1) {
            switch (stmt.charAt(++offset)) {
                case 'l':
                case 'L':
                    return alterCheck(stmt, offset);
                case 'n':
                case 'N':
                    return analyzeCheck(stmt, offset);
                default:
                    return OTHER;
            }
        }
        return OTHER;
    }

    private int alterCheck(String stmt, int offset) {
        if (stmt.length() > offset + 3) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            if ((c1 == 'T' || c1 == 't') && (c2 == 'E' || c2 == 'e') && (c3 == 'R' || c3 == 'r')) {
                return alterViewCheck(stmt, offset);
            }
        }
        return OTHER;
    }

    private int alterViewCheck(String stmt, int offset) {
        while (true) {
            if (!(stmt.charAt(++offset) == ' ' || stmt.charAt(offset) == '\t' || stmt.charAt(offset) == '\r' || stmt.charAt(offset) == '\n')) {
                char c1 = stmt.charAt(offset);
                char c2 = stmt.charAt(++offset);
                char c3 = stmt.charAt(++offset);
                char c4 = stmt.charAt(++offset);
                char c5 = stmt.charAt(++offset);
                if ((c1 == 'v' || c1 == 'V') && (c2 == 'i' || c2 == 'I') && (c3 == 'e' || c3 == 'E') && (c4 == 'w' || c4 == 'W') &&
                        (c5 == ' ' || c5 == '\t' || c5 == '\r' || c5 == '\n')) {
                    return ALTER_VIEW;
                } else {
                    return DDL;
                }
            }
        }
    }

    private int analyzeCheck(String stmt, int offset) {
        if (stmt.length() > offset + 5) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            if ((c1 == 'A' || c1 == 'a') && (c2 == 'L' || c2 == 'l') && (c3 == 'Y' || c3 == 'y') &&
                    (c4 == 'Z' || c4 == 'z') && (c5 == 'E' || c5 == 'e') && (c6 == ' ' || c6 == '\t' || c6 == '\r' || c6 == '\n')) {
                return UNSUPPORT;
            }
        }
        return OTHER;
    }

    //create table/view/...
    private int createCheck(String stmt, int offset) {
        int len = stmt.length();
        if (len > offset + 6) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            if ((c1 == 'R' || c1 == 'r') && (c2 == 'E' || c2 == 'e') && (c3 == 'A' || c3 == 'a') && (c4 == 'T' || c4 == 't') &&
                    (c5 == 'E' || c5 == 'e')) {
                while (len > ++offset) {
                    if (ParseUtil.isSpace(stmt.charAt(offset))) {
                        continue;
                    }
                    char c6 = stmt.charAt(offset);
                    if (c6 == 'd' || c6 == 'D') {
                        return databaseCheck(stmt, offset);
                    } else if (c6 == 'v' || c6 == 'V') {
                        return viewCheck(stmt, offset, false);
                    } else if (c6 == 'o' || c6 == 'O') {
                        return orCheck(stmt, offset);
                    } else if (c6 == 't' || c6 == 'T') {
                        if (len > ++offset) {
                            char c7 = stmt.charAt(offset);
                            if (c7 == 'e' || c7 == 'E') {
                                return createTempTableCheck(stmt, offset);
                            }
                        }
                        return DDL;
                    }
                }
                return DDL;
            }
        }
        return OTHER;
    }


    /**
     * check the sql is create database
     *
     * @param stmt
     * @param offset
     * @return
     */
    private int databaseCheck(String stmt, int offset) {
        int len = stmt.length();
        if (len > offset + 8) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            char c7 = stmt.charAt(++offset);
            if ((c1 == 'a' || c1 == 'A') && (c2 == 't' || c2 == 'T') && (c3 == 'A' || c3 == 'a') &&
                    (c4 == 'b' || c4 == 'B') && (c5 == 'a' || c5 == 'A') && (c6 == 's' || c6 == 'S') &&
                    (c7 == 'e' || c7 == 'E') && ParseUtil.isSpace(stmt.charAt(++offset))) {
                return CREATE_DATABASE;
            }
        }
        return DDL;
    }

    /**
     * check the sql is create replace view /create view/others
     *
     * @param stmt
     * @param offset
     * @return
     */
    private int orCheck(String stmt, int offset) {
        int len = stmt.length();
        if (len > ++offset) {
            char c1 = stmt.charAt(offset);
            if ((c1 == 'R' || c1 == 'r')) {
                while (len > ++offset) {
                    if (ParseUtil.isSpace(stmt.charAt(offset))) {
                        continue;
                    }
                    return replaceViewCheck(stmt, offset);
                }
            }
        }
        return DDL;
    }


    private int viewCheck(String stmt, int offset, boolean isReplace) {
        int len = stmt.length();
        if (len > offset + 4) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            if ((c1 == 'I' || c1 == 'i') && (c2 == 'E' || c2 == 'e') &&
                    (c3 == 'W' || c3 == 'w') && ParseUtil.isSpace(stmt.charAt(++offset))) {
                if (isReplace) {
                    return REPLACE_VIEW;
                } else {
                    return CREATE_VIEW;
                }
            }
        }
        return DDL;
    }

    private int replaceViewCheck(String stmt, int offset) {
        int len = stmt.length();
        if (len > offset + 7) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            if ((c1 == 'E' || c1 == 'e') && (c2 == 'P' || c2 == 'p') && (c3 == 'L' || c3 == 'l') &&
                    (c4 == 'A' || c4 == 'a') && (c5 == 'C' || c5 == 'c') && (c6 == 'E' || c6 == 'e')) {
                while (len > ++offset) {
                    if (ParseUtil.isSpace(stmt.charAt(offset))) {
                        continue;
                    }
                    return viewCheck(stmt, offset, true);
                }
            }
        }
        return DDL;
    }

    //drop
    private int dropCheck(String stmt, int offset) {
        if (stmt.length() > offset + 3) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'R' || c1 == 'r') && (c2 == 'O' || c2 == 'o') && (c3 == 'P' || c3 == 'p') &&
                    (c4 == ' ' || c4 == '\t' || c4 == '\r' || c4 == '\n')) {
                while (stmt.length() > ++offset) {
                    switch (stmt.charAt(offset)) {
                        case ' ':
                        case '\t':
                        case '\r':
                        case '\n':
                            continue;
                        case 'V':
                        case 'v':
                            return dropViewCheck(stmt, offset);
                        case 'P':
                        case 'p':
                            return dropPrepareCheck(stmt, offset);
                        case 't':
                        case 'T':
                            return dropTableCheck(stmt, offset);
                        default:
                            return DDL;
                    }
                }
            }
        }
        return OTHER;
    }

    private int dropPrepareCheck(String stmt, int offset) {
        if (isPrepare(stmt, offset)) return SCRIPT_PREPARE;
        return DDL;
    }

    private int dropViewCheck(String stmt, int offset) {
        if (stmt.length() > offset + 3) {
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            if ((c2 == 'i' || c2 == 'I') &&
                    (c3 == 'e' || c3 == 'E') &&
                    (c4 == 'w' || c4 == 'W') &&
                    (c5 == ' ' || c5 == '\t' || c5 == '\r' || c5 == '\n')) {
                return DROP_VIEW;
            }
        }
        return DDL;
    }

    // delete or drop
    private int deleteOrdCheck(String stmt, int offset) {
        int sqlType;
        switch (stmt.charAt((offset + 1))) {
            case 'E':
            case 'e':
                sqlType = dCheck(stmt, offset);
                break;
            case 'R':
            case 'r':
                sqlType = dropCheck(stmt, offset);
                break;
            default:
                sqlType = OTHER;
        }
        return sqlType;
    }

    // HELP' '
    private int helpCheck(String stmt, int offset) {
        if (stmt.length() > offset + "ELP ".length()) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            if ((c1 == 'E' || c1 == 'e') && (c2 == 'L' || c2 == 'l') &&
                    (c3 == 'P' || c3 == 'p')) {
                return (offset << 8) | HELP;
            }
        }
        return OTHER;
    }

    //EXECUTE' '
    private int executeCheck(String stmt, int offset) {
        if (stmt.length() > offset + "CUTE ".length()) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            if ((c1 == 'C' || c1 == 'c') && (c2 == 'U' || c2 == 'u') && (c3 == 'T' || c3 == 't') && (c4 == 'E' || c4 == 'e') &&
                    (c5 == ' ' || c5 == '\t' || c5 == '\r' || c5 == '\n')) {
                return SCRIPT_PREPARE;
            }
        }
        return OTHER;
    }

    // EXPLAIN' '
    private int explainCheck(String stmt, int offset) {
        if (stmt.length() > offset + "LAIN ".length()) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            if ((c1 == 'L' || c1 == 'l') && (c2 == 'A' || c2 == 'a') && (c3 == 'I' || c3 == 'i') && (c4 == 'N' || c4 == 'n')) {
                if (ParseUtil.isSpaceOrLeft(c5)) {
                    return (offset << 8) | EXPLAIN;
                } else if (c5 == '2' && (stmt.length() > offset + 1) && ParseUtil.isSpace(stmt.charAt(++offset))) {
                    return (offset << 8) | EXPLAIN2;
                } else {
                    return OTHER;
                }
            }
        }
        return OTHER;
    }

    // KILL' '
    protected int killCheck(String stmt, int offset) {
        if (stmt.length() > offset + "ILL ".length()) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'I' || c1 == 'i') && (c2 == 'L' || c2 == 'l') &&
                    (c3 == 'L' || c3 == 'l') &&
                    (c4 == ' ' || c4 == '\t' || c4 == '\r' || c4 == '\n')) {
                while (stmt.length() > ++offset) {
                    switch (stmt.charAt(offset)) {
                        case ' ':
                        case '\t':
                        case '\r':
                        case '\n':
                            continue;
                        case 'Q':
                        case 'q':
                            return killQueryCheck(stmt, offset);
                        case 'c':
                        case 'C':
                            return killConnection(stmt, offset);
                        default:
                            return (offset << 8) | KILL;
                    }
                }
                return OTHER;
            }
        }
        return OTHER;
    }

    // KILL QUERY' '
    private int killQueryCheck(String stmt, int offset) {
        if (stmt.length() > offset + "UERY ".length()) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            if ((c1 == 'U' || c1 == 'u') && (c2 == 'E' || c2 == 'e') &&
                    (c3 == 'R' || c3 == 'r') && (c4 == 'Y' || c4 == 'y') &&
                    (c5 == ' ' || c5 == '\t' || c5 == '\r' || c5 == '\n')) {
                while (stmt.length() > ++offset) {
                    switch (stmt.charAt(offset)) {
                        case ' ':
                        case '\t':
                        case '\r':
                        case '\n':
                            continue;
                        default:
                            return (offset << 8) | KILL_QUERY;
                    }
                }
                return OTHER;
            }
        }
        return OTHER;
    }

    // BEGIN
    protected int beginCheck(String stmt, int offset) {
        String key = "work";
        if (stmt.length() > offset + 4) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'E' || c1 == 'e') &&
                    (c2 == 'G' || c2 == 'g') &&
                    (c3 == 'I' || c3 == 'i') &&
                    (c4 == 'N' || c4 == 'n') &&
                    (stmt.length() == ++offset || keyCheck(stmt, key, offset) || ParseUtil.isEOF(stmt, offset) || ParseUtil.isMultiEof(stmt, offset))) {
                return BEGIN;
            }
        }
        return OTHER;
    }


    private boolean keyCheck(String stmt, String key, int offset) {
        String lowerStmt = stmt.toLowerCase().substring(offset).trim();
        offset = stmt.toLowerCase().indexOf(key) + key.length() - 1;
        if (lowerStmt.startsWith(key) && (stmt.length() == ++offset || ParseUtil.isEOF(stmt, offset) || ParseUtil.isMultiEof(stmt, offset))) {
            return true;
        }
        return false;
    }

    // COMMIT
    private int commitCheck(String stmt, int offset) {
        String key = "work";
        if (stmt.length() > offset + 5) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            if ((c1 == 'O' || c1 == 'o') &&
                    (c2 == 'M' || c2 == 'm') &&
                    (c3 == 'M' || c3 == 'm') &&
                    (c4 == 'I' || c4 == 'i') &&
                    (c5 == 'T' || c5 == 't') &&
                    (stmt.length() == ++offset || keyCheck(stmt, key, offset) || ParseUtil.isEOF(stmt, offset) || ParseUtil.isMultiEof(stmt, offset))) {
                return COMMIT;
            }
        }

        return OTHER;
    }

    // CALL
    private int callCheck(String stmt, int offset) {
        if (stmt.length() > offset + 3) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            if ((c1 == 'A' || c1 == 'a') && (c2 == 'L' || c2 == 'l') &&
                    (c3 == 'L' || c3 == 'l')) {
                return CALL;
            }
        }

        return OTHER;
    }

    private int checksumCheck(String stmt, int offset) {
        if (stmt.length() > offset + "HECKSUM ".length()) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            char c7 = stmt.charAt(++offset);
            char c8 = stmt.charAt(++offset);
            if ((c1 == 'H' || c1 == 'h') && (c2 == 'E' || c2 == 'e') && (c3 == 'C' || c3 == 'c') && (c4 == 'K' || c4 == 'k') &&
                    (c5 == 'S' || c5 == 's') && (c6 == 'U' || c6 == 'u') && (c7 == 'M' || c7 == 'm') &&
                    (c8 == ' ' || c8 == '\t' || c8 == '\r' || c8 == '\n')) {
                return UNSUPPORT;
            }
        }
        return OTHER;
    }

    protected int cCheck(String stmt, int offset) {
        int sqlType;
        switch (stmt.charAt((offset + 1))) {
            case 'A':
            case 'a':
                sqlType = callCheck(stmt, offset);
                break;
            case 'H':
            case 'h':
                sqlType = checksumCheck(stmt, offset);
                break;
            case 'O':
            case 'o':
                sqlType = commitCheck(stmt, offset);
                break;
            case 'R':
            case 'r':
                sqlType = createCheck(stmt, offset);
                break;
            default:
                sqlType = OTHER;
        }
        return sqlType;
    }

    // DESCRIBE or desc or DELETE' ' or DEALLOCATE' '
    private int dCheck(String stmt, int offset) {
        int sqlType = OTHER;
        if (stmt.length() > offset + 1) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            if ((c1 == 'E' || c1 == 'e')) {
                switch (c2) {
                    case 'A':
                    case 'a':
                        sqlType = dealCheck(stmt, offset);
                        break;
                    case 'S':
                    case 's':
                        sqlType = descCheck(stmt, offset);
                        break;
                    case 'L':
                    case 'l':
                        sqlType = deleCheck(stmt, offset);
                        break;
                    default:
                        break;
                }
            }
        }
        return sqlType;
    }

    private int dealCheck(String stmt, int offset) {
        if (stmt.length() > offset + "LLOCATE ".length()) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            char c7 = stmt.charAt(++offset);
            char c8 = stmt.charAt(++offset);
            if ((c1 == 'L' || c1 == 'l') && (c2 == 'L' || c2 == 'l') && (c3 == 'O' || c3 == 'o') && (c4 == 'C' || c4 == 'c') &&
                    (c5 == 'A' || c5 == 'a') && (c6 == 'T' || c6 == 't') && (c7 == 'E' || c7 == 'e') &&
                    (c8 == ' ' || c8 == '\t' || c8 == '\r' || c8 == '\n')) {
                return SCRIPT_PREPARE;
            }
        }
        return OTHER;
    }

    private int descCheck(String stmt, int offset) {
        if (stmt.length() > offset + "C ".length()) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            if (c1 == 'C' || c1 == 'c') {
                if (c2 == ' ' || c2 == '\t' || c2 == '\r' || c2 == '\n') {
                    return DESCRIBE;
                } else if (c2 == 'R' || c2 == 'r') {
                    if (stmt.length() > offset + "IBE ".length()) {
                        char c3 = stmt.charAt(++offset);
                        char c4 = stmt.charAt(++offset);
                        char c5 = stmt.charAt(++offset);
                        char c6 = stmt.charAt(++offset);
                        if ((c3 == 'I' || c3 == 'i') && (c4 == 'B' || c4 == 'b') && (c5 == 'E' || c5 == 'e') &&
                                (c6 == ' ' || c6 == '\t' || c6 == '\r' || c6 == '\n')) {
                            return DESCRIBE;
                        }
                    }
                }
            }
        }
        return OTHER;
    }

    private int deleCheck(String stmt, int offset) {
        if (stmt.length() > offset + "ETE ".length()) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'E' || c1 == 'e') && (c2 == 'T' || c2 == 't') && (c3 == 'E' || c3 == 'e') &&
                    (c4 == ' ' || c4 == '\t' || c4 == '\r' || c4 == '\n')) {
                return DELETE;
            }
        }
        return OTHER;
    }

    // INSERT' '
    protected int insertCheck(String stmt, int offset) {
        if (stmt.length() > offset + 6) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            if ((c1 == 'N' || c1 == 'n') && (c2 == 'S' || c2 == 's') &&
                    (c3 == 'E' || c3 == 'e') && (c4 == 'R' || c4 == 'r') &&
                    (c5 == 'T' || c5 == 't') &&
                    (c6 == ' ' || c6 == '\t' || c6 == '\r' || c6 == '\n')) {
                return INSERT;
            }
        }
        return OTHER;
    }


    protected int release(String stmt, int offset) {
        if (stmt.length() > offset + 5) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            if ((c1 == 'e' || c1 == 'E') && (c2 == 'a' || c2 == 'A') && (c3 == 's' || c3 == 'S') &&
                    (c4 == 'e' || c4 == 'E') && ParseUtil.isSpace(c5)) {
                return RELEASE_SAVEPOINT;
            }
        }
        return OTHER;
    }

    protected int repCheck(String stmt, int offset) {
        if (stmt.length() > ++offset) {
            switch (stmt.charAt(offset)) {
                case 'A':
                case 'a':
                    return repair(stmt, offset);
                case 'L':
                case 'l':
                    return replace(stmt, offset);
                default:
                    return OTHER;
            }
        }
        return OTHER;
    }


    private int repair(String stmt, int offset) {
        if (stmt.length() > offset + 3) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            if ((c1 == 'I' || c1 == 'i') && (c2 == 'R' || c2 == 'r') && (c3 == ' ' || c3 == '\t' || c3 == '\r' || c3 == '\n')) {
                return UNSUPPORT;
            }
        }
        return OTHER;
    }


    // REPLACE' '
    private int replace(String stmt, int offset) {
        if (stmt.length() > offset + 5) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'A' || c1 == 'a') && (c2 == 'C' || c2 == 'c') && (c3 == 'E' || c3 == 'e') &&
                    (c4 == ' ' || c4 == '\t' || c4 == '\r' || c4 == '\n')) {
                return REPLACE;
            }
        }
        return OTHER;
    }

    // ROLLBACK
    protected int rollbackCheck(String stmt, int offset) {
        int len = stmt.length();
        if (len > offset + 6) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            if ((c1 == 'L' || c1 == 'l') && (c2 == 'L' || c2 == 'l') &&
                    (c3 == 'B' || c3 == 'b') && (c4 == 'A' || c4 == 'a') &&
                    (c5 == 'C' || c5 == 'c') && (c6 == 'K' || c6 == 'k')) {
                char tmp;
                while (len > ++offset) {
                    tmp = stmt.charAt(offset);
                    if (ParseUtil.isSpace(tmp)) {
                        continue;
                    }
                    switch (tmp) {
                        case '/':
                            offset = ParseUtil.comment(stmt, offset);
                            break;
                        case 't':
                        case 'T':
                            return ROLLBACK_SAVEPOINT;
                        case 'w':
                        case 'W':
                            return rollbackWorkCheck(stmt, offset);
                        default:
                            break;
                    }
                }
                return ROLLBACK;
            }
        }
        return OTHER;
    }

    protected int rollbackWorkCheck(String stmt, int offset) {
        if (stmt.length() > offset + 3) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            if ((c1 == 'O' || c1 == 'o') && (c2 == 'R' || c2 == 'r') &&
                    (c3 == 'K' || c3 == 'k')) {
                if (stmt.length() == ++offset || ParseUtil.isEOF(stmt, offset) || ParseUtil.isMultiEof(stmt, offset)) {
                    return ROLLBACK;
                }
            }
        }
        return OTHER;
    }


    // SAVEPOINT
    protected int savepointCheck(String stmt, int offset) {
        if (stmt.length() > offset + 8) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            char c5 = stmt.charAt(++offset);
            char c6 = stmt.charAt(++offset);
            char c7 = stmt.charAt(++offset);
            char c8 = stmt.charAt(++offset);
            if ((c1 == 'V' || c1 == 'v') && (c2 == 'E' || c2 == 'e') &&
                    (c3 == 'P' || c3 == 'p') && (c4 == 'O' || c4 == 'o') &&
                    (c5 == 'I' || c5 == 'i') && (c6 == 'N' || c6 == 'n') &&
                    (c7 == 'T' || c7 == 't') &&
                    (c8 == ' ' || c8 == '\t' || c8 == '\r' || c8 == '\n')) {
                return SAVEPOINT;
            }
        }
        return OTHER;
    }

    protected int seCheck(String stmt, int offset) {
        if (stmt.length() > ++offset) {
            switch (stmt.charAt(offset)) {
                case 'L':
                case 'l':
                    return selectCheck(stmt, offset);
                case 'T':
                case 't':
                    if (stmt.length() > ++offset) {
                        //support QUERY like this
                        //  /*!dble: sql=SELECT * FROM test where id=99 */set @pin=1;
                        //  call p_test(@pin,@pout);
                        //  select @pout;
                        if (stmt.startsWith("/*!" + Versions.ANNOTATION_NAME) || stmt.startsWith("/*#" + Versions.ANNOTATION_NAME) || stmt.startsWith("/*" + Versions.ANNOTATION_NAME)) {
                            Matcher matcher = CALL_PATTERN.matcher(stmt);
                            if (matcher.find()) {
                                return CALL;
                            }
                        }

                        char c = stmt.charAt(offset);
                        if (c == ' ' || c == '\r' || c == '\n' || c == '\t' || c == '/' || c == '#') {
                            return (offset << 8) | SET;
                        }
                    }
                    return OTHER;
                default:
                    return OTHER;
            }
        }
        return OTHER;
    }

    // SELECT' '
    private int selectCheck(String stmt, int offset) {
        if (stmt.length() > offset + 4) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            char c4 = stmt.charAt(++offset);
            if ((c1 == 'E' || c1 == 'e') &&
                    (c2 == 'C' || c2 == 'c') &&
                    (c3 == 'T' || c3 == 't') &&
                    (c4 == ' ' || c4 == '\t' || c4 == '\r' || c4 == '\n' || c4 == '/' || c4 == '#')) {
                return (offset << 8) | SELECT;
            }
        }
        return OTHER;
    }

    // SHOW' '
    protected int showCheck(String stmt, int offset) {
        if (stmt.length() > offset + 3) {
            char c1 = stmt.charAt(++offset);
            char c2 = stmt.charAt(++offset);
            char c3 = stmt.charAt(++offset);
            if ((c1 == 'O' || c1 == 'o') && (c2 == 'W' || c2 == 'w') &&
                    (c3 == ' ' || c3 == '\t' || c3 == '\r' || c3 == '\n')) {
                return (offset << 8) | SHOW;
            }
        }
        return OTHER;
    }


    // KILL CONNECTION' 'XXXXXX
    private int killConnection(String stmt, int offset) {
        final String keyword = "CONNECTION";
        if (ParseUtil.compare(stmt, offset, keyword)) {
            offset = offset + keyword.length();
            if (stmt.length() > offset && ParseUtil.isSpace(stmt.charAt(offset)) && ParseUtil.isErrorTail(offset + 1, stmt)) {
                return (offset << 8) | KILL;
            }
        }
        return OTHER;
    }

}
