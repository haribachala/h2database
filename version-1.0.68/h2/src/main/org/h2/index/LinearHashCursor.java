/*
 * Copyright 2004-2008 H2 Group. Licensed under the H2 License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.index;

import java.sql.SQLException;

import org.h2.result.Row;
import org.h2.result.SearchRow;

/**
 * The cursor implementation for the linear hash index.
 */
public class LinearHashCursor implements Cursor {
    private Row row;
    private boolean end;

    LinearHashCursor(Row row) {
        this.row = row;
    }

    public Row get() {
        return row;
    }

    public SearchRow getSearchRow() throws SQLException {
        return row;
    }

    public int getPos() {
        return row.getPos();
    }

    public boolean next() {
        if (row == null || end) {
            row = null;
            return false;
        }
        end = true;
        return true;
    }

}