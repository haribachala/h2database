/*
 * Copyright 2004-2008 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.schema;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;

import org.h2.constant.ErrorCode;
import org.h2.constant.SysProperties;
import org.h2.constraint.Constraint;
import org.h2.engine.Constants;
import org.h2.engine.Database;
import org.h2.engine.DbObject;
import org.h2.engine.DbObjectBase;
import org.h2.engine.Session;
import org.h2.engine.User;
import org.h2.index.Index;
import org.h2.message.Message;
import org.h2.message.Trace;
import org.h2.table.Table;
import org.h2.table.TableData;
import org.h2.table.TableLink;
import org.h2.util.ObjectArray;

/**
 * A schema as created by the SQL statement
 * CREATE SCHEMA
 */
public class Schema extends DbObjectBase {

    private User owner;
    private boolean system;

    private HashMap tablesAndViews = new HashMap();
    private HashMap indexes = new HashMap();
    private HashMap sequences = new HashMap();
    private HashMap triggers = new HashMap();
    private HashMap constraints = new HashMap();
    private HashMap constants = new HashMap();

    /*
     * Set of returned unique names that are not yet stored
     * (to avoid returning the same unique name twice when multiple threads
     * concurrently create objects).
     */
    private HashSet temporaryUniqueNames = new HashSet();

    public Schema(Database database, int id, String schemaName, User owner, boolean system) {
        super(database, id, schemaName, Trace.SCHEMA);
        this.owner = owner;
        this.system = system;
    }

    public boolean canDrop() {
        return !getName().equals(Constants.SCHEMA_INFORMATION) && !getName().equals(Constants.SCHEMA_MAIN);
    }

    public String getCreateSQLForCopy(Table table, String quotedName) {
        throw Message.getInternalError();
    }

    public String getDropSQL() {
        return null;
    }

    public String getCreateSQL() {
        if (system) {
            return null;
        }
        StringBuffer buff = new StringBuffer();
        buff.append("CREATE SCHEMA ");
        buff.append(getSQL());
        buff.append(" AUTHORIZATION ");
        buff.append(owner.getSQL());
        return buff.toString();
    }

    public int getType() {
        return DbObject.SCHEMA;
    }

    public void removeChildrenAndResources(Session session) throws SQLException {
        while (triggers != null && triggers.size() > 0) {
            TriggerObject obj = (TriggerObject) triggers.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (constraints != null && constraints.size() > 0) {
            Constraint obj = (Constraint) constraints.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (tablesAndViews != null && tablesAndViews.size() > 0) {
            Table obj = (Table) tablesAndViews.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (indexes != null && indexes.size() > 0) {
            Index obj = (Index) indexes.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (sequences != null && sequences.size() > 0) {
            Sequence obj = (Sequence) sequences.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (constants != null && constants.size() > 0) {
            Constant obj = (Constant) constants.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        database.removeMeta(session, getId());
        owner = null;
        invalidate();
    }

    public void checkRename() throws SQLException {
    }

    public User getOwner() {
        return owner;
    }

    private HashMap getMap(int type) {
        switch (type) {
        case DbObject.TABLE_OR_VIEW:
            return tablesAndViews;
        case DbObject.SEQUENCE:
            return sequences;
        case DbObject.INDEX:
            return indexes;
        case DbObject.TRIGGER:
            return triggers;
        case DbObject.CONSTRAINT:
            return constraints;
        case DbObject.CONSTANT:
            return constants;
        default:
            throw Message.getInternalError("type=" + type);
        }
    }

    public void add(SchemaObject obj) throws SQLException {
        if (SysProperties.CHECK && obj.getSchema() != this) {
            throw Message.getInternalError("wrong schema");
        }
        String name = obj.getName();
        HashMap map = getMap(obj.getType());
        if (SysProperties.CHECK && map.get(name) != null) {
            throw Message.getInternalError("object already exists");
        }
        map.put(name, obj);
        freeUniqueName(name);
    }

    public void rename(SchemaObject obj, String newName) throws SQLException {
        int type = obj.getType();
        HashMap map = getMap(type);
        if (SysProperties.CHECK) {
            if (!map.containsKey(obj.getName())) {
                throw Message.getInternalError("not found: " + obj.getName());
            }
            if (obj.getName().equals(newName) || map.containsKey(newName)) {
                throw Message.getInternalError("object already exists: " + newName);
            }
        }
        map.remove(obj.getName());
        freeUniqueName(obj.getName());
        obj.rename(newName);
        map.put(newName, obj);
        freeUniqueName(newName);
    }

    public Table findTableOrView(Session session, String name) {
        Table table = (Table) tablesAndViews.get(name);
        if (table == null && session != null) {
            table = session.findLocalTempTable(name);
        }
        return table;
    }

    public Index findIndex(String name) {
        return (Index) indexes.get(name);
    }

    public TriggerObject findTrigger(String name) {
        return (TriggerObject) triggers.get(name);
    }

    public Sequence findSequence(String sequenceName) {
        return (Sequence) sequences.get(sequenceName);
    }

    public Constraint findConstraint(String constraintName) {
        return (Constraint) constraints.get(constraintName);
    }

    public Constant findConstant(String constantName) {
        return (Constant) constants.get(constantName);
    }

    public void freeUniqueName(String name) {
        if (name != null) {
            temporaryUniqueNames.remove(name);
        }
    }

    private String getUniqueName(DbObject obj, HashMap map, String prefix) {
        String hash = Integer.toHexString(obj.getName().hashCode()).toUpperCase();
        String name = null;
        for (int i = 1; i < hash.length(); i++) {
            name = prefix + hash.substring(0, i);
            if (!map.containsKey(name) && !temporaryUniqueNames.contains(name)) {
                break;
            }
            name = null;
        }
        if (name == null) {
            prefix = prefix + hash + "_";
            for (int i = 0;; i++) {
                name = prefix + i;
                if (!map.containsKey(name) && !temporaryUniqueNames.contains(name)) {
                    break;
                }
            }
        }
        temporaryUniqueNames.add(name);
        return name;
    }

    public String getUniqueConstraintName(DbObject obj) {
        return getUniqueName(obj, constraints, "CONSTRAINT_");
    }

    public String getUniqueIndexName(DbObject obj, String prefix) {
        return getUniqueName(obj, indexes, prefix);
    }

    public Table getTableOrView(Session session, String name) throws SQLException {
        Table table = (Table) tablesAndViews.get(name);
        if (table == null && session != null) {
            table = session.findLocalTempTable(name);
        }
        if (table == null) {
            throw Message.getSQLException(ErrorCode.TABLE_OR_VIEW_NOT_FOUND_1, name);
        }
        return table;
    }

    public Index getIndex(String name) throws SQLException {
        Index index = (Index) indexes.get(name);
        if (index == null) {
            throw Message.getSQLException(ErrorCode.INDEX_NOT_FOUND_1, name);
        }
        return index;
    }

    public Constraint getConstraint(String name) throws SQLException {
        Constraint constraint = (Constraint) constraints.get(name);
        if (constraint == null) {
            throw Message.getSQLException(ErrorCode.CONSTRAINT_NOT_FOUND_1, name);
        }
        return constraint;
    }

    public Constant getConstant(Session session, String constantName) throws SQLException {
        Constant constant = (Constant) constants.get(constantName);
        if (constant == null) {
            throw Message.getSQLException(ErrorCode.CONSTANT_NOT_FOUND_1, constantName);
        }
        return constant;
    }

    public Sequence getSequence(String sequenceName) throws SQLException {
        Sequence sequence = (Sequence) sequences.get(sequenceName);
        if (sequence == null) {
            throw Message.getSQLException(ErrorCode.SEQUENCE_NOT_FOUND_1, sequenceName);
        }
        return sequence;
    }

    public ObjectArray getAll(int type) {
        HashMap map = getMap(type);
        return new ObjectArray(map.values());
    }

    public void remove(Session session, SchemaObject obj) throws SQLException {
        String objName = obj.getName();
        HashMap map = getMap(obj.getType());
        if (SysProperties.CHECK && !map.containsKey(objName)) {
            throw Message.getInternalError("not found: " + objName);
        }
        map.remove(objName);
        freeUniqueName(objName);
    }

    public TableData createTable(String tempName, int id, ObjectArray newColumns, boolean persistent, boolean clustered)
            throws SQLException {
        return new TableData(this, tempName, id, newColumns, persistent, clustered);
    }

    public TableLink createTableLink(int id, String tableName, String driver, String url, String user, String password,
            String originalTable, boolean emitUpdates, boolean force) throws SQLException {
        return new TableLink(this, id, tableName, driver, url, user, password, originalTable, emitUpdates, force);
    }

}