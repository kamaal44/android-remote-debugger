/*
 * Copyright 2020 Arman Sargsyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zerobranch.androidremotedebugger.source.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import zerobranch.androidremotedebugger.source.local.StatusCodeFilter;
import zerobranch.androidremotedebugger.source.models.httplog.HttpLogModel;
import zerobranch.androidremotedebugger.source.models.httplog.QueryType;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class HttpLogRepository {
    private static final String REMOTE_NET_LOGS_TABLE_NAME = "net_log_data";
    private final SQLiteDatabase database;
    private final Gson gson = new Gson();

    public HttpLogRepository(SQLiteDatabase database) {
        this.database = database;
    }

    public long add(HttpLogModel model) {
        ContentValues values = new ContentValues();
        values.put(NetLogTable.CODE, model.code);
        values.put(NetLogTable.TIME, model.time);
        values.put(NetLogTable.DURATION, model.duration);
        values.put(NetLogTable.BODY_SIZE, model.bodySize);
        values.put(NetLogTable.QUERY_ID, model.queryId);
        values.put(NetLogTable.PORT, model.port);
        values.put(NetLogTable.METHOD, model.method);
        values.put(NetLogTable.QUERY_TYPE, model.queryType.name());
        values.put(NetLogTable.MESSAGE, model.message);
        values.put(NetLogTable.FULL_STATUS, model.fullStatus);
        values.put(NetLogTable.FULL_IP_ADDRESS, model.fullIpAddress);
        values.put(NetLogTable.REQUEST_CONTENT_TYPE, model.requestContentType);
        values.put(NetLogTable.IP, model.ip);
        values.put(NetLogTable.URL, model.url);
        values.put(NetLogTable.BODY, model.body);
        values.put(NetLogTable.ERROR_MESSAGE, model.errorMessage);
        values.put(NetLogTable.HEADERS, gson.toJson(model.headers));

        return database.insert(REMOTE_NET_LOGS_TABLE_NAME, null, values);
    }

    public void clearAll() {
        database.delete(REMOTE_NET_LOGS_TABLE_NAME, null, null);
    }

    public void createHttpLogsTable(SQLiteDatabase db) {
        final String query = "create table " + REMOTE_NET_LOGS_TABLE_NAME + " (" +
                NetLogTable.ID + " integer primary key autoincrement, " +
                NetLogTable.QUERY_ID + " text, " +
                NetLogTable.METHOD + " text," +
                NetLogTable.CODE + " integer," +
                NetLogTable.MESSAGE + " text," +
                NetLogTable.FULL_STATUS + " text," +
                NetLogTable.FULL_IP_ADDRESS + " text," +
                NetLogTable.QUERY_TYPE + " text," +
                NetLogTable.TIME + " text," +
                NetLogTable.DURATION + " text," +
                NetLogTable.REQUEST_CONTENT_TYPE + " text," +
                NetLogTable.BODY_SIZE + " text," +
                NetLogTable.PORT + " text," +
                NetLogTable.IP + " text," +
                NetLogTable.URL + " text," +
                NetLogTable.BODY + " text," +
                NetLogTable.ERROR_MESSAGE + " text," +
                NetLogTable.HEADERS + " text);";
        db.execSQL(query);
    }

    public List<HttpLogModel> getHttpLogs(int offset,
                                          int limit,
                                          StatusCodeFilter statusCodeFilter,
                                          boolean isOnlyErrors,
                                          String search
    ) {
        final StringBuilder query = new StringBuilder()
                .append("select * from " + REMOTE_NET_LOGS_TABLE_NAME);

        final StringBuilder conditionBuilder = new StringBuilder();
        final List<String> arguments = new ArrayList<>();

        if (isOnlyErrors) {
            conditionBuilder
                    .append("(")
                    .append(NetLogTable.ERROR_MESSAGE)
                    .append(" is not null ")
                    .append(" or ")
                    .append(" (")
                    .append(NetLogTable.CODE)
                    .append(" >= 400 ")
                    .append(" and ")
                    .append(NetLogTable.CODE)
                    .append(" <= 599)")
                    .append(")");
        } else if (statusCodeFilter != null && statusCodeFilter.isExistCondition()) {
            conditionBuilder
                    .append("(")
                    .append(NetLogTable.CODE)
                    .append(" >= ")
                    .append(statusCodeFilter.minStatusCode)
                    .append(" and ")
                    .append(NetLogTable.CODE)
                    .append(" <= ")
                    .append(statusCodeFilter.maxStatusCode)
                    .append(")");
        }

        if (!TextUtils.isEmpty(search)) {
            final StringBuilder searchBuilder = new StringBuilder();

            String[] tables = new String[]{
                    NetLogTable.QUERY_ID,
                    NetLogTable.METHOD,
                    NetLogTable.TIME,
                    NetLogTable.CODE,
                    NetLogTable.MESSAGE,
                    NetLogTable.FULL_STATUS,
                    NetLogTable.FULL_IP_ADDRESS,
                    NetLogTable.REQUEST_CONTENT_TYPE,
                    NetLogTable.PORT,
                    NetLogTable.IP,
                    NetLogTable.URL,
                    NetLogTable.BODY_SIZE,
                    NetLogTable.DURATION,
                    NetLogTable.BODY,
                    NetLogTable.ERROR_MESSAGE,
                    NetLogTable.HEADERS
            };

            for (int i = 0; i < tables.length; i++) {
                if (i != 0) {
                    searchBuilder.append(" or ");
                }

                searchBuilder.append(tables[i]).append(" like ?");
                arguments.add("%".concat(search).concat("%"));
            }

            if (conditionBuilder.length() != 0) {
                conditionBuilder
                        .append(" and (")
                        .append(searchBuilder)
                        .append(")");
            } else {
                conditionBuilder.append(searchBuilder);
            }
        }

        if (conditionBuilder.length() != 0) {
            query.append(" where ").append(conditionBuilder);
        }

        query.append(" order by ")
                .append(NetLogTable.ID)
                .append(" limit ")
                .append(limit);

        if (offset != -1) {
            query.append(" offset ").append(offset);
        }

        final Cursor cursor = database.rawQuery(query.toString(), arguments.toArray(new String[0]));
        final List<HttpLogModel> logModels = new ArrayList<>();

        while (cursor.moveToNext()) {
            HttpLogModel httpLogModel = new HttpLogModel();
            httpLogModel.id = cursor.getLong(cursor.getColumnIndex(NetLogTable.ID));
            httpLogModel.method = cursor.getString(cursor.getColumnIndex(NetLogTable.METHOD));
            httpLogModel.queryId = cursor.getString(cursor.getColumnIndex(NetLogTable.QUERY_ID));
            httpLogModel.queryType = QueryType.valueOf(cursor.getString(cursor.getColumnIndex(NetLogTable.QUERY_TYPE)));

            int code = cursor.getInt(cursor.getColumnIndex(NetLogTable.CODE));
            httpLogModel.code = (code != 0) ? code : null;

            httpLogModel.message = cursor.getString(cursor.getColumnIndex(NetLogTable.MESSAGE));
            httpLogModel.time = cursor.getString(cursor.getColumnIndex(NetLogTable.TIME));
            httpLogModel.duration = cursor.getString(cursor.getColumnIndex(NetLogTable.DURATION));
            httpLogModel.requestContentType = cursor.getString(cursor.getColumnIndex(NetLogTable.REQUEST_CONTENT_TYPE));
            httpLogModel.bodySize = cursor.getString(cursor.getColumnIndex(NetLogTable.BODY_SIZE));
            httpLogModel.port = cursor.getString(cursor.getColumnIndex(NetLogTable.PORT));
            httpLogModel.ip = cursor.getString(cursor.getColumnIndex(NetLogTable.IP));
            httpLogModel.fullIpAddress = cursor.getString(cursor.getColumnIndex(NetLogTable.FULL_IP_ADDRESS));
            httpLogModel.fullStatus = cursor.getString(cursor.getColumnIndex(NetLogTable.FULL_STATUS));
            httpLogModel.url = cursor.getString(cursor.getColumnIndex(NetLogTable.URL));
            httpLogModel.errorMessage = cursor.getString(cursor.getColumnIndex(NetLogTable.ERROR_MESSAGE));
            httpLogModel.body = cursor.getString(cursor.getColumnIndex(NetLogTable.BODY));

            Type listType = new TypeToken<List<String>>() {}.getType();
            httpLogModel.headers = gson.fromJson(cursor.getString(cursor.getColumnIndex(NetLogTable.HEADERS)), listType);

            logModels.add(httpLogModel);
        }

        cursor.close();
        return logModels;
    }

    private interface NetLogTable {
        String ID = "_id";
        String QUERY_ID = "query_id";
        String QUERY_TYPE = "query_type";
        String METHOD = "method";
        String PORT = "port";
        String IP = "ip";
        String URL = "url";
        String CODE = "code";
        String MESSAGE = "message";
        String FULL_STATUS = "full_status";
        String FULL_IP_ADDRESS = "full_ip_address";
        String TIME = "time";
        String DURATION = "duration";
        String REQUEST_CONTENT_TYPE = "request_content_type";
        String BODY_SIZE = "body_size";
        String BODY = "body";
        String HEADERS = "headers";
        String ERROR_MESSAGE = "error_message";
    }
}