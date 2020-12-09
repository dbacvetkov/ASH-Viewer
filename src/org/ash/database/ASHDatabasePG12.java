/*
 *-------------------
 * The ASHDatabasePG12.java is part of ASH Viewer
 *-------------------
 *
 * ASH Viewer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ASH Viewer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ASH Viewer.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2009, Alex Kardapolov, All rights reserved.
 *
 */
package org.ash.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.math.BigInteger;

import org.ash.conn.model.Model;
import org.ash.datamodel.ActiveSessionHistory;
import org.ash.datamodel.AshIdTime;
import org.ash.datamodel.AshSqlIdTypeText;
import org.ash.datatemp.SessionsTemp;
import org.ash.datatemp.SqlsTemp;
import org.ash.util.Utils;
import org.ash.database.DBUtils;
import org.jdesktop.swingx.treetable.TreeTableModel;
import org.jfree.data.xy.CategoryTableXYDataset;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Sequence;
import com.sleepycat.persist.EntityCursor;
import com.sleepycat.persist.EntityStore;

// dcvetkov import
import org.ash.util.Options;

/**
 * The Class ASHDatabasePG12.
 */
public class ASHDatabasePG12 extends ASHDatabase {

    /**
     * The model.
     */
    private Model model;

    /**
     * The sequence.
     */
    private Sequence seq;

    /**
     * The BDB store.
     */
    private EntityStore store;

    /**
     * The BDB dao.
     */
    private AshDataAccessor dao;

    /**
     * The range for sqls and sessions temp (gantt)
     */
    private int rangeHalf = 7500;

    /**
     * The query ash.
     */
    private String queryASH = "SELECT current_timestamp, "
            + "datname, pid, usesysid, usename, "
            + "application_name, backend_type, "
            + "coalesce(client_hostname, client_addr::text, 'localhost') as client_hostname, "
            + "wait_event_type, wait_event, query, "
            + "coalesce(query_start, xact_start, backend_start) as query_start, 1000 * EXTRACT(EPOCH FROM (clock_timestamp()-coalesce(query_start, xact_start, backend_start))) as duration "
            + "from pg_stat_activity "
            + "where state='active' and pid != pg_backend_pid()";

    private String fileSeparator = System.getProperty("file.separator");
    private String planDir = Options.getInstance().getPlanDir();



    /**
     * The k for sample_id after reconnect
     */
    private long kReconnect = 0;

    /**
     * Is reconnect
     */
    private boolean isReconnect = false;

    /**
     * Instantiates a new PG12 Database
     *
     * @param model0 the model0
     */
    public ASHDatabasePG12(Model model0) {
        super(model0);
        this.model = model0;
        this.store = super.getStore();
        this.dao = super.getDao();
    }

    /* (non-Javadoc)
     * @see org.ash.database.DatabaseMain#loadToLocalBDB()
     */
    public void loadToLocalBDB() {

        // Get max value of ash
        super.initializeVarsOnLoad();

        // Load data to activeSessionHistoryById
        loadHistAshDataToLocal();

	// dcvetkov
	// super.loadToSubByEventAnd10Sec();
	super.loadToSubByEventAnd10Sec_all_history_at_start();
    }

    /* (non-Javadoc)
     * @see org.ash.database.DatabaseMain#loadToLocalBDBCollector()
     */
    public synchronized void loadToLocalBDBCollector() {
        // Get max value of ash
	// it does: sampleId = dao.ashById.sortedMap().lastKey();
        super.initializeVarsAfterLoad9i();

        // Load data to activeSessionHistoryById
        loadAshDataToLocal();

    }

    /* (non-Javadoc)
     * @see org.ash.database.DatabaseMain#loadDataToChartPanelDataSet(org.jfree.data.xy.CategoryTableXYDataset)
     */
    public void loadDataToChartPanelDataSet(CategoryTableXYDataset _dataset) {
        super.loadDataToChartPanelDataSet(_dataset);
    }

    /* (non-Javadoc)
     * @see org.ash.database.DatabaseMain#updateDataToChartPanelDataSet()
     */
    public void updateDataToChartPanelDataSet() {
        super.updateDataToChartPanelDataSet();
    }

    /**
     * Load ash data to local.
     */
    private void loadAshDataToLocal() {

        ResultSet resultSetAsh = null;
        PreparedStatement statement = null;
        Connection conn = null;

        // Get sequence activeSessionHistoryId
        try {
            seq = store.getSequence("activeSessionHistoryId");
        } catch (DatabaseException e) {
            // e.printStackTrace();
        }

        try {

            if (model.getConnectionPool() != null) {

		String connDBName = getParameter("ASH.db");
		int explainFreq = Options.getInstance().getExplainFreq();

                conn = this.model.getConnectionPool().getConnection();

                statement = conn.prepareStatement(this.queryASH);






                // set ArraySize for current statement to improve performance
                statement.setFetchSize(5000);
		// set Timeout to 1 second
                statement.setQueryTimeout(1);

                resultSetAsh = statement.executeQuery();

                while (resultSetAsh.next()) {

                    long activeSessionHistoryIdWait = 0;
                    try {
                        activeSessionHistoryIdWait = seq.get(null, 1);
                    } catch (DatabaseException e) {
                        e.printStackTrace();
                    }

                    // Calculate sample time
                    java.sql.Timestamp PGDateSampleTime = resultSetAsh.getTimestamp("current_timestamp");
                    Long valueSampleIdTimeLongWait = (new Long(PGDateSampleTime.getTime()));







                    java.sql.Timestamp queryStartTS = resultSetAsh.getTimestamp("query_start");
		    Long queryStartLong = 0L;
                    if (queryStartTS != null) {
			queryStartLong = (new Long(queryStartTS.getTime()));
                    }

                    Double duration = resultSetAsh.getDouble("duration");

                    Long sessionId = resultSetAsh.getLong("pid");
                    String backendType = resultSetAsh.getString("backend_type");

                    String databaseName = resultSetAsh.getString("datname");

                    Long userId = resultSetAsh.getLong("usesysid");
                    String userName = resultSetAsh.getString("usename");

                    String program = resultSetAsh.getString("application_name");

                    String query_text = resultSetAsh.getString("query");
                    if ((query_text == null) || (query_text.equals(""))) {
			if(program != null && program.equals("pg_basebackup")) {
                                query_text = "backup";
			} else if (program != null && (program.equals("walreceiver") || program.equals("walsender"))) {
                                query_text = "wal";
                        } else if(backendType != null && (backendType.equals("walreceiver") || backendType.equals("walsender"))) {
                                query_text = "wal";
                        } else {
                                query_text = "empty";
			}
                    }

                    String query_text_norm = DBUtils.NormalizeSQL(query_text);
                    String command_type = DBUtils.GetSQLCommandType(query_text_norm);
                    String sqlId = DBUtils.md5Custom(query_text_norm);

                    /* System.out.println(query_text);
                    System.out.println(query_text_norm);
                    System.out.println(sqlId);
                    System.out.println("");
                     */
                    String hostname = resultSetAsh.getString("client_hostname");
                    String event = resultSetAsh.getString("wait_event");
                    String waitClass = resultSetAsh.getString("wait_event_type");

                    if ((waitClass == null) || (waitClass.equals(""))) {
                        waitClass = "CPU";
                        event = "CPU";
                    }

                    if ((event == null) || (event.equals(""))) {
                        event = waitClass;
                    }

                    if (event.equals("PgSleep")&&query_text.contains("pg_stat_activity_snapshot")) {
			continue;
		    }

                    Double waitClassId = 0.0;

                    if (waitClass.equals("CPU")) {
                        waitClassId = 0.0;
                    } else if (waitClass.equals("IO")) {
                        waitClassId = 1.0;
                    } else if (waitClass.equals("Lock")) {
                        waitClassId = 2.0;
                    } else if (waitClass.equals("LWLock")) {
                        waitClassId = 3.0;
                    } else if (waitClass.equals("BufferPin")) {
                        waitClassId = 4.0;
                    } else if (waitClass.equals("Activity")) {
                        waitClassId = 5.0;
                    } else if (waitClass.equals("Extension")) {
                        waitClassId = 6.0;
                    } else if (waitClass.equals("Client")) {
                        waitClassId = 7.0;
                    } else if (waitClass.equals("IPC")) {
                        waitClassId = 8.0;
                    } else if (waitClass.equals("Timeout")) {
                        waitClassId = 9.0;
                    }

                    // Create row for wait event
                    try {
                        dao.ashById.putNoOverwrite(new AshIdTime(valueSampleIdTimeLongWait, valueSampleIdTimeLongWait.doubleValue()));
                    } catch (DatabaseException e) {
                        e.printStackTrace();
                    }

                    // Load data for active session history (wait event)
                    try {
                        dao.activeSessionHistoryById
                                .putNoReturn(new ActiveSessionHistory(
                                        activeSessionHistoryIdWait,
                                        valueSampleIdTimeLongWait,
                                        sessionId, backendType, userId, userName, sqlId, command_type,
                                        event, waitClass, waitClassId, program, hostname, queryStartLong, duration));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // dcvetkov: Load data for sqlid: command type and query text
                    try {
                        dao.ashSqlIdTypeTextId.putNoReturn(new AshSqlIdTypeText(sqlId, command_type, query_text_norm));
                    } catch (DatabaseException e) {
                        e.printStackTrace();
                    }

                    // dcvetkov: explain plan
                    if (command_type.equals("SELECT") || command_type.equals("UPDATE") || command_type.equals("DELETE") || command_type.equals("INSERT")) {
			if ((connDBName.length() > 0) && (explainFreq > 0)) {
			    DBUtils.explainPlan(sqlId, query_text, command_type, planDir, fileSeparator, connDBName, databaseName, conn, explainFreq);
			    // DBUtils.FindQueryID(sqlId, query_text_norm, conn);
			}
                    }

                }
                if (conn != null) {
                    model.getConnectionPool().free(conn);
                }
            } else {
                // Connect is lost
                setReconnect(true);
                model.closeConnectionPool();
                model.connectionPoolInitReconnect();
            }

        } catch (SQLException e) {
            System.out.println("SQL Exception occured: " + e.getMessage());
            model.closeConnectionPool();
        } finally {
            if (resultSetAsh != null) {
                try {
                    resultSetAsh.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /*
     * (non-Javadoc)
     *
     * @see org.ash.database.DatabaseMain#calculateSqlsSessionsData(double,
     *      double)
     */
    public void calculateSqlsSessionsData(double beginTime, double endTime, String eventFlag) {

        try {

            SqlsTemp tmpSqlsTemp = null;
            SessionsTemp tmpSessionsTemp = null;

            if (eventFlag.equalsIgnoreCase("All")) {
                tmpSqlsTemp = super.getSqlsTemp();
                tmpSessionsTemp = super.getSessionsTemp();
            } else {
                tmpSqlsTemp = super.getSqlsTempDetail();
                tmpSessionsTemp = super.getSessionsTempDetail();
            }

            // get sample id's for beginTime and endTime
            EntityCursor<AshIdTime> ashSampleIds;
            ashSampleIds = dao.doRangeQuery(dao.ashBySampleTime, beginTime - rangeHalf, true, endTime + rangeHalf, true);
            /* Iterate on Ash by SampleTime. */
            Iterator<AshIdTime> ashIter = ashSampleIds.iterator();

            while (ashIter.hasNext()) {

                AshIdTime ashSumMain = ashIter.next();

                // get rows from ActiveSessionHistory for samplId
                EntityCursor<ActiveSessionHistory> ActiveSessionHistoryCursor;
                ActiveSessionHistoryCursor = dao.doRangeQuery(dao.activeSessionHistoryByAshId, ashSumMain.getsampleId(), true, ashSumMain.getsampleId(), true);
                Iterator<ActiveSessionHistory> ActiveSessionHistoryIter = ActiveSessionHistoryCursor.iterator();

                while (ActiveSessionHistoryIter.hasNext()) {
                    ActiveSessionHistory ASH = ActiveSessionHistoryIter.next();

                    // sql data
                    String sqlId = ASH.getSqlId();
                    double waitClassId = ASH.getWaitClassId();
                    // session data
                    Long sessionId = (Long) ASH.getSessionId();
                    String sessionidS = sessionId.toString().trim();
                    Long useridL = (Long) ASH.getUserId();
                    String usernameSess = ASH.getUserName();
                    String programSess = ASH.getProgram();
                    programSess = programSess + "@" + ASH.getHostname();
                    String backendSess = ASH.getBackendType();
                    String waitClass = ASH.getWaitClass();
                    String eventName = ASH.getEvent();

                    Long queryStart = ASH.getQueryStart();
                    Double duration = ASH.getDuration();

                    // Exit when current eventClas != eventFlag
                    if (!eventFlag.equalsIgnoreCase("All")) {
                        if (waitClass != null && waitClass.equalsIgnoreCase(eventFlag)) {
                            this.loadDataToTempSqlSession(tmpSqlsTemp,
                                    tmpSessionsTemp, sqlId, waitClassId,
                                    sessionId, sessionidS, 0.0, backendSess,
                                    useridL, usernameSess, programSess, true, eventName, queryStart, duration);
                        }

                    } else {
                        this.loadDataToTempSqlSession(tmpSqlsTemp,
                                tmpSessionsTemp, sqlId, waitClassId,
                                sessionId, sessionidS, 0.0, backendSess,
                                useridL, usernameSess, programSess, false, eventFlag, queryStart, duration);
                    }
                }
                // Close cursor!!
                ActiveSessionHistoryCursor.close();
            }
            tmpSqlsTemp.set_sum();
            tmpSessionsTemp.set_sum();

            // Close cursor!!
            ashSampleIds.close();

        } catch (DatabaseException e) {
            e.printStackTrace();
        }
    }


    /*
     * (non-Javadoc)
     *
     * @see org.ash.database.DatabaseMain#loadCommandTypeFromDB(java.util.List)
     */
    public void loadSqlTextCommandTypeFromDB(List<String> arraySqlId) {

        // Load all sqlId
        ArrayList<String> sqlIdAll = new ArrayList<String>();

        Iterator<String> arraySqlIdIter = arraySqlId.iterator();
        while (arraySqlIdIter.hasNext()) {
            String sqlId = arraySqlIdIter.next();
            if (!isSqlTextExist(sqlId)) {
                sqlIdAll.add(sqlId);
            }
        }

        this.loadSqlTextSqlIdFromDB(sqlIdAll);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.ash.database.DatabaseMain#isSqlPlanHashValueExist(java.lang.String)
     */
    public boolean isSqlTextExist(String sqlId) {
        boolean res = false;
        try {
            res = dao.getAshSqlIdTypeTextId().contains(sqlId);
        } catch (DatabaseException e) {
            res = false;
        }

        return res;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.ash.database.DatabaseMain#getSqlType(java.lang.String)
     */
    public String getSqlType(String sqlId) {
        String sqlType = null;
        try {
            AshSqlIdTypeText ash = dao.ashSqlIdTypeTextId.get(sqlId);
            if (ash != null) {
                sqlType = ash.getCommandType();
            } else {
                sqlType = "";
            }
        } catch (DatabaseException e) {
            sqlType = "";
            e.printStackTrace();
        }
        return sqlType;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.ash.database.DatabaseMain#getSqlText(java.lang.String)
     */
    public String getSqlText(String sqlId) {
        String sqlText = null;
        try {
            AshSqlIdTypeText ash = dao.ashSqlIdTypeTextId.get(sqlId);
            if (ash != null) {
                sqlText = ash.getSqlText();
            } else {
                sqlText = "";
            }

        } catch (DatabaseException e) {
            sqlText = "";
            e.printStackTrace();
        }
        return sqlText;
    }

    /**
     * Load data to temporary sql and sessions (gantt data)
     *
     * @param tmpSqlsTemp
     * @param tmpSessionsTemp
     * @param sqlId
     * @param waitClassId
     * @param sessionId
     * @param sessionidS
     * @param sessionSerial
     * @param sessioniSerialS
     * @param useridL
     * @param programSess
     * @param isDetail
     * @param sqlPlanHashValue
     */
    private void loadDataToTempSqlSession(SqlsTemp tmpSqlsTemp,
            SessionsTemp tmpSessionsTemp, String sqlId, double waitClassId, Long sessionId,
            String sessionidS, Double sessionSerial, String backendType,
            Long useridL, String usernameSess, String programSess, boolean isDetail,
            String eventDetail, Long queryStart, Double duration) {

        int count = 1;

        /**
         * Save data for sql row
         */
        if (sqlId != null) {
            // Save SQL_ID and init
            tmpSqlsTemp.setSqlId(sqlId);
            // Save group event
            tmpSqlsTemp.setTimeOfGroupEvent(sqlId, waitClassId, count);
	    tmpSqlsTemp.setSqlStats(sqlId, sessionId, queryStart, duration);
        }

        /**
         * Save data for session row
         */
        tmpSessionsTemp.setSessionId(sessionidS, backendType, programSess, "", usernameSess);
        tmpSessionsTemp.setTimeOfGroupEvent(sessionidS, waitClassId, count);

        /**
         * Save event detail data for sql and sessions row
         */
        if (isDetail) {
            if (sqlId != null) {
                tmpSqlsTemp.setTimeOfEventName(sqlId, waitClassId, eventDetail, count);
            }
            tmpSessionsTemp.setTimeOfEventName(sessionidS, waitClassId, eventDetail, count);
        }
    }

    /**
     * @return the kReconnect
     */
    private long getKReconnect() {
        return kReconnect;
    }

    /**
     * @param reconnect the kReconnect to set
     */
    private void setKReconnect(long reconnect) {
        kReconnect = reconnect;
    }

    /**
     * @return the isReconnect
     */
    private boolean isReconnect() {
        return isReconnect;
    }

    /**
     * @param isReconnect the isReconnect to set
     */
    private void setReconnect(boolean isReconnect) {
        this.isReconnect = isReconnect;
    }







    private void loadHistAshDataToLocal() {

        ResultSet resultSetAsh = null;
        PreparedStatement statement = null;
        Connection conn = null;

        // Get sequence activeSessionHistoryId
        try {
            seq = store.getSequence("activeSessionHistoryId");
        } catch (DatabaseException e) {
            // e.printStackTrace();
        }

        try {

            if (model.getConnectionPool() != null) {

                conn = this.model.getConnectionPool().getConnection();

                statement = conn.prepareStatement("select * from pg_stat_activity_history order by sample_time");

                // set ArraySize for current statement to improve performance
                statement.setFetchSize(5000);

		try {
                	resultSetAsh = statement.executeQuery();
	        } catch (SQLException e) {
			return;
		}

                while (resultSetAsh.next()) {

                    long activeSessionHistoryIdWait = 0;
                    try {
                        activeSessionHistoryIdWait = seq.get(null, 1);
                    } catch (DatabaseException e) {
                        e.printStackTrace();
                    }

                    // Calculate sample time
                    java.sql.Timestamp PGDateSampleTime = resultSetAsh.getTimestamp("sample_time");
                    Long valueSampleIdTimeLongWait = (new Long(PGDateSampleTime.getTime()));

                    java.sql.Timestamp queryStartTS = resultSetAsh.getTimestamp("query_start");
		    Long queryStartLong = 0L;
                    if (queryStartTS != null) {
			queryStartLong = (new Long(queryStartTS.getTime()));
                    }

                    Double duration = resultSetAsh.getDouble("duration");

                    Long sessionId = resultSetAsh.getLong("pid");
                    String backendType = resultSetAsh.getString("backend_type");

                    String databaseName = resultSetAsh.getString("datname");

                    Long userId = resultSetAsh.getLong("usesysid");
                    String userName = resultSetAsh.getString("usename");

                    String program = resultSetAsh.getString("application_name");

                    String query_text = resultSetAsh.getString("query");
                    if ((query_text == null) || (query_text.equals(""))) {
                        if (program.equals("pg_basebackup")) {
                            query_text = "backup";
                        } else if (program.equals("walreceiver") || program.equals("walsender")) {
                            query_text = "wal";
                        } else if (backendType.equals("walreceiver") || backendType.equals("walsender")) {
                            query_text = "wal";
                        } else {
                            query_text = "empty";
                        }
                    }
                    String query_text_norm = DBUtils.NormalizeSQL(query_text);
                    String command_type = DBUtils.GetSQLCommandType(query_text_norm);
                    String sqlId = DBUtils.md5Custom(query_text_norm);

                    /* System.out.println(query_text);
                    System.out.println(query_text_norm);
                    System.out.println(sqlId);
                    System.out.println("");
                     */
                    String hostname = resultSetAsh.getString("client_hostname");
                    String event = resultSetAsh.getString("wait_event");
                    String waitClass = resultSetAsh.getString("wait_event_type");

                    if (waitClass == null) {
                        waitClass = "CPU";
                        event = "CPU";
                    }

                    if ((event == null) || (event.equals(""))) {
                        event = waitClass;
                    }

                    if (event.equals("PgSleep")&&query_text.contains("pg_stat_activity_snapshot")) {
			continue;
		    }

                    Double waitClassId = 0.0;

                    if (waitClass.equals("CPU")) {
                        waitClassId = 0.0;
                    } else if (waitClass.equals("IO")) {
                        waitClassId = 1.0;
                    } else if (waitClass.equals("Lock")) {
                        waitClassId = 2.0;
                    } else if (waitClass.equals("LWLock")) {
                        waitClassId = 3.0;
                    } else if (waitClass.equals("BufferPin")) {
                        waitClassId = 4.0;
                    } else if (waitClass.equals("Activity")) {
                        waitClassId = 5.0;
                    } else if (waitClass.equals("Extension")) {
                        waitClassId = 6.0;
                    } else if (waitClass.equals("Client")) {
                        waitClassId = 7.0;
                    } else if (waitClass.equals("IPC")) {
                        waitClassId = 8.0;
                    } else if (waitClass.equals("Timeout")) {
                        waitClassId = 9.0;
                    }

                    // Create row for wait event
                    try {
                        dao.ashById.putNoOverwrite(new AshIdTime(valueSampleIdTimeLongWait, valueSampleIdTimeLongWait.doubleValue()));
                    } catch (DatabaseException e) {
                        e.printStackTrace();
                    }

                    // Load data for active session history (wait event)
                    try {
                        dao.activeSessionHistoryById
                                .putNoReturn(new ActiveSessionHistory(
                                        activeSessionHistoryIdWait,
                                        valueSampleIdTimeLongWait,
                                        sessionId, backendType, userId, userName, sqlId, command_type,
                                        event, waitClass, waitClassId, program, hostname, queryStartLong, duration));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // dcvetkov: Load data for sqlid: command type and query text
                    try {
                        dao.ashSqlIdTypeTextId.putNoReturn(new AshSqlIdTypeText(sqlId, command_type, query_text_norm));
                    } catch (DatabaseException e) {
                        e.printStackTrace();
                    }
                }
                if (conn != null) {
                    model.getConnectionPool().free(conn);
                }
            } else {
                // Connect is lost
                setReconnect(true);
                model.closeConnectionPool();
                model.connectionPoolInitReconnect();
            }

        } catch (SQLException e) {
            System.out.println("SQL Exception occured: " + e.getMessage());
            model.closeConnectionPool();
        } finally {
            if (resultSetAsh != null) {
                try {
                    resultSetAsh.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
