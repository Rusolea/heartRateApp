package com.heartratemonitor.heartratemonitor.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.heartratemonitor.heartratemonitor.models.HeartRateData;
import com.heartratemonitor.heartratemonitor.models.WorkoutSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper para manejar la base de datos SQLite de la aplicación
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    
    // Información de la base de datos
    private static final String DATABASE_NAME = "heart_rate_monitor.db";
    private static final int DATABASE_VERSION = 1;
    
    // Tabla de sesiones
    private static final String TABLE_SESSIONS = "sessions";
    private static final String COLUMN_SESSION_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_START_TIME = "start_time";
    private static final String COLUMN_END_TIME = "end_time";
    private static final String COLUMN_ACTIVITY_TYPE = "activity_type";
    private static final String COLUMN_AVG_HEART_RATE = "avg_heart_rate";
    private static final String COLUMN_MAX_HEART_RATE = "max_heart_rate";
    private static final String COLUMN_CALORIES = "calories";
    private static final String COLUMN_ZONE_1 = "zone_1_time";
    private static final String COLUMN_ZONE_2 = "zone_2_time";
    private static final String COLUMN_ZONE_3 = "zone_3_time";
    private static final String COLUMN_ZONE_4 = "zone_4_time";
    private static final String COLUMN_ZONE_5 = "zone_5_time";
    private static final String COLUMN_SDNN = "sdnn";
    private static final String COLUMN_RMSSD = "rmssd";
    private static final String COLUMN_PNN50 = "pnn50";
    private static final String COLUMN_LFHF = "lf_hf_ratio";
    private static final String COLUMN_HRV_SCORE = "hrv_score";
    
    // Tabla de datos de frecuencia cardíaca
    private static final String TABLE_HEART_RATE_DATA = "heart_rate_data";
    private static final String COLUMN_HR_ID = "id";
    private static final String COLUMN_SESSION_ID_FK = "session_id";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_HEART_RATE = "heart_rate";
    private static final String COLUMN_RR_INTERVAL = "rr_interval";
    
    // Comandos SQL para crear tablas
    private static final String CREATE_TABLE_SESSIONS = 
            "CREATE TABLE " + TABLE_SESSIONS + "(" +
                    COLUMN_SESSION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TITLE + " TEXT, " +
                    COLUMN_START_TIME + " INTEGER, " +
                    COLUMN_END_TIME + " INTEGER, " +
                    COLUMN_ACTIVITY_TYPE + " TEXT, " +
                    COLUMN_AVG_HEART_RATE + " INTEGER, " +
                    COLUMN_MAX_HEART_RATE + " INTEGER, " +
                    COLUMN_CALORIES + " INTEGER, " +
                    COLUMN_ZONE_1 + " INTEGER, " +
                    COLUMN_ZONE_2 + " INTEGER, " +
                    COLUMN_ZONE_3 + " INTEGER, " +
                    COLUMN_ZONE_4 + " INTEGER, " +
                    COLUMN_ZONE_5 + " INTEGER, " +
                    COLUMN_SDNN + " REAL, " +
                    COLUMN_RMSSD + " REAL, " +
                    COLUMN_PNN50 + " REAL, " +
                    COLUMN_LFHF + " REAL, " +
                    COLUMN_HRV_SCORE + " INTEGER" +
                    ")";
    
    private static final String CREATE_TABLE_HEART_RATE_DATA = 
            "CREATE TABLE " + TABLE_HEART_RATE_DATA + "(" +
                    COLUMN_HR_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_SESSION_ID_FK + " INTEGER, " +
                    COLUMN_TIMESTAMP + " INTEGER, " +
                    COLUMN_HEART_RATE + " INTEGER, " +
                    COLUMN_RR_INTERVAL + " INTEGER, " +
                    "FOREIGN KEY(" + COLUMN_SESSION_ID_FK + ") REFERENCES " + 
                    TABLE_SESSIONS + "(" + COLUMN_SESSION_ID + ") ON DELETE CASCADE" +
                    ")";
    
    private static DatabaseHelper instance;
    
    // Constructor
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    // Singleton para evitar múltiples instancias
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SESSIONS);
        db.execSQL(CREATE_TABLE_HEART_RATE_DATA);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // En caso de actualizar la estructura de la base de datos
        if (oldVersion < newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_HEART_RATE_DATA);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
            onCreate(db);
        }
    }
    
    /**
     * Inserta una nueva sesión en la base de datos
     * @param session Sesión a insertar
     * @return ID de la sesión insertada
     */
    public long insertSession(WorkoutSession session) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_TITLE, session.getTitle());
        values.put(COLUMN_START_TIME, session.getStartTime());
        values.put(COLUMN_END_TIME, session.getEndTime());
        values.put(COLUMN_ACTIVITY_TYPE, session.getActivityType());
        values.put(COLUMN_AVG_HEART_RATE, session.getAverageHeartRate());
        values.put(COLUMN_MAX_HEART_RATE, session.getMaxHeartRate());
        values.put(COLUMN_CALORIES, session.getCaloriesBurned());
        values.put(COLUMN_ZONE_1, session.getTimeInZone1());
        values.put(COLUMN_ZONE_2, session.getTimeInZone2());
        values.put(COLUMN_ZONE_3, session.getTimeInZone3());
        values.put(COLUMN_ZONE_4, session.getTimeInZone4());
        values.put(COLUMN_ZONE_5, session.getTimeInZone5());
        values.put(COLUMN_SDNN, session.getSdnn());
        values.put(COLUMN_RMSSD, session.getRmssd());
        values.put(COLUMN_PNN50, session.getPnn50());
        values.put(COLUMN_LFHF, session.getLfhfRatio());
        values.put(COLUMN_HRV_SCORE, session.getHrvScore());
        
        long id = db.insert(TABLE_SESSIONS, null, values);
        db.close();
        
        return id;
    }
    
    /**
     * Actualiza una sesión existente
     * @param session Sesión a actualizar
     * @return Número de filas afectadas
     */
    public int updateSession(WorkoutSession session) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_TITLE, session.getTitle());
        values.put(COLUMN_END_TIME, session.getEndTime());
        values.put(COLUMN_ACTIVITY_TYPE, session.getActivityType());
        values.put(COLUMN_AVG_HEART_RATE, session.getAverageHeartRate());
        values.put(COLUMN_MAX_HEART_RATE, session.getMaxHeartRate());
        values.put(COLUMN_CALORIES, session.getCaloriesBurned());
        values.put(COLUMN_ZONE_1, session.getTimeInZone1());
        values.put(COLUMN_ZONE_2, session.getTimeInZone2());
        values.put(COLUMN_ZONE_3, session.getTimeInZone3());
        values.put(COLUMN_ZONE_4, session.getTimeInZone4());
        values.put(COLUMN_ZONE_5, session.getTimeInZone5());
        values.put(COLUMN_SDNN, session.getSdnn());
        values.put(COLUMN_RMSSD, session.getRmssd());
        values.put(COLUMN_PNN50, session.getPnn50());
        values.put(COLUMN_LFHF, session.getLfhfRatio());
        values.put(COLUMN_HRV_SCORE, session.getHrvScore());
        
        int result = db.update(TABLE_SESSIONS, values, 
                COLUMN_SESSION_ID + " = ?", 
                new String[] { String.valueOf(session.getId()) });
        db.close();
        
        return result;
    }
    
    /**
     * Obtiene todas las sesiones almacenadas
     * @return Lista de sesiones
     */
    public List<WorkoutSession> getAllSessions() {
        List<WorkoutSession> sessionList = new ArrayList<>();
        
        String selectQuery = "SELECT * FROM " + TABLE_SESSIONS + 
                             " ORDER BY " + COLUMN_START_TIME + " DESC";
        
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        
        if (cursor.moveToFirst()) {
            do {
                WorkoutSession session = new WorkoutSession();
                session.setId(cursor.getLong(cursor.getColumnIndex(COLUMN_SESSION_ID)));
                session.setTitle(cursor.getString(cursor.getColumnIndex(COLUMN_TITLE)));
                session.setStartTime(cursor.getLong(cursor.getColumnIndex(COLUMN_START_TIME)));
                session.setEndTime(cursor.getLong(cursor.getColumnIndex(COLUMN_END_TIME)));
                session.setActivityType(cursor.getString(cursor.getColumnIndex(COLUMN_ACTIVITY_TYPE)));
                session.setAverageHeartRate(cursor.getInt(cursor.getColumnIndex(COLUMN_AVG_HEART_RATE)));
                session.setMaxHeartRate(cursor.getInt(cursor.getColumnIndex(COLUMN_MAX_HEART_RATE)));
                session.setCaloriesBurned(cursor.getInt(cursor.getColumnIndex(COLUMN_CALORIES)));
                session.setTimeInZone1(cursor.getInt(cursor.getColumnIndex(COLUMN_ZONE_1)));
                session.setTimeInZone2(cursor.getInt(cursor.getColumnIndex(COLUMN_ZONE_2)));
                session.setTimeInZone3(cursor.getInt(cursor.getColumnIndex(COLUMN_ZONE_3)));
                session.setTimeInZone4(cursor.getInt(cursor.getColumnIndex(COLUMN_ZONE_4)));
                session.setTimeInZone5(cursor.getInt(cursor.getColumnIndex(COLUMN_ZONE_5)));
                session.setSdnn(cursor.getDouble(cursor.getColumnIndex(COLUMN_SDNN)));
                session.setRmssd(cursor.getDouble(cursor.getColumnIndex(COLUMN_RMSSD)));
                session.setPnn50(cursor.getDouble(cursor.getColumnIndex(COLUMN_PNN50)));
                session.setLfhfRatio(cursor.getDouble(cursor.getColumnIndex(COLUMN_LFHF)));
                
                sessionList.add(session);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        
        return sessionList;
    }
    
    /**
     * Obtiene una sesión por su ID
     * @param sessionId ID de la sesión
     * @return Sesión o null si no existe
     */
    public WorkoutSession getSessionById(long sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        WorkoutSession session = null;
        
        try {
            cursor = db.query(TABLE_SESSIONS, null, 
                    COLUMN_SESSION_ID + " = ?", 
                    new String[] { String.valueOf(sessionId) }, 
                    null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                session = new WorkoutSession();
                session.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                session.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)));
                session.setStartTime(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_START_TIME)));
                session.setEndTime(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_END_TIME)));
                
                // Usar getColumnIndex para campos opcionales y verificar si existen
                int activityTypeIndex = cursor.getColumnIndex(COLUMN_ACTIVITY_TYPE);
                if (activityTypeIndex != -1 && !cursor.isNull(activityTypeIndex)) {
                    session.setActivityType(cursor.getString(activityTypeIndex));
                }
                
                // Campos numéricos necesarios para la visualización del historial
                int avgHrIndex = cursor.getColumnIndex(COLUMN_AVG_HEART_RATE);
                if (avgHrIndex != -1 && !cursor.isNull(avgHrIndex)) {
                    session.setAverageHeartRate(cursor.getInt(avgHrIndex));
                }
                
                int maxHrIndex = cursor.getColumnIndex(COLUMN_MAX_HEART_RATE);
                if (maxHrIndex != -1 && !cursor.isNull(maxHrIndex)) {
                    session.setMaxHeartRate(cursor.getInt(maxHrIndex));
                }
                
                int caloriesIndex = cursor.getColumnIndex(COLUMN_CALORIES);
                if (caloriesIndex != -1 && !cursor.isNull(caloriesIndex)) {
                    session.setCaloriesBurned(cursor.getInt(caloriesIndex));
                }
                
                // Zonas de frecuencia cardíaca
                int zone1Index = cursor.getColumnIndex(COLUMN_ZONE_1);
                if (zone1Index != -1 && !cursor.isNull(zone1Index)) {
                    session.setTimeInZone1(cursor.getInt(zone1Index));
                }
                
                int zone2Index = cursor.getColumnIndex(COLUMN_ZONE_2);
                if (zone2Index != -1 && !cursor.isNull(zone2Index)) {
                    session.setTimeInZone2(cursor.getInt(zone2Index));
                }
                
                int zone3Index = cursor.getColumnIndex(COLUMN_ZONE_3);
                if (zone3Index != -1 && !cursor.isNull(zone3Index)) {
                    session.setTimeInZone3(cursor.getInt(zone3Index));
                }
                
                int zone4Index = cursor.getColumnIndex(COLUMN_ZONE_4);
                if (zone4Index != -1 && !cursor.isNull(zone4Index)) {
                    session.setTimeInZone4(cursor.getInt(zone4Index));
                }
                
                int zone5Index = cursor.getColumnIndex(COLUMN_ZONE_5);
                if (zone5Index != -1 && !cursor.isNull(zone5Index)) {
                    session.setTimeInZone5(cursor.getInt(zone5Index));
                }
                
                // Datos de HRV - los que pueden estar causando problemas
                int sdnnIndex = cursor.getColumnIndex(COLUMN_SDNN);
                if (sdnnIndex != -1 && !cursor.isNull(sdnnIndex)) {
                    session.setSdnn(cursor.getDouble(sdnnIndex));
                }
                
                int rmssdIndex = cursor.getColumnIndex(COLUMN_RMSSD);
                if (rmssdIndex != -1 && !cursor.isNull(rmssdIndex)) {
                    session.setRmssd(cursor.getDouble(rmssdIndex));
                }
                
                int pnn50Index = cursor.getColumnIndex(COLUMN_PNN50);
                if (pnn50Index != -1 && !cursor.isNull(pnn50Index)) {
                    session.setPnn50(cursor.getDouble(pnn50Index));
                }
                
                int lfhfIndex = cursor.getColumnIndex(COLUMN_LFHF);
                if (lfhfIndex != -1 && !cursor.isNull(lfhfIndex)) {
                    session.setLfhfRatio(cursor.getDouble(lfhfIndex));
                }
                
                // Puntuación HRV
                int hrvScoreIndex = cursor.getColumnIndex(COLUMN_HRV_SCORE);
                if (hrvScoreIndex != -1 && !cursor.isNull(hrvScoreIndex)) {
                    session.setHrvScore(cursor.getInt(hrvScoreIndex));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error al obtener sesión por ID: " + e.getMessage());
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        
        return session;
    }
    
    /**
     * Elimina una sesión por su ID
     * @param sessionId ID de la sesión a eliminar
     * @return true si se eliminó correctamente
     */
    public boolean deleteSession(long sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_SESSIONS, 
                COLUMN_SESSION_ID + " = ?", 
                new String[] { String.valueOf(sessionId) });
        db.close();
        
        return result > 0;
    }
    
    /**
     * Inserta un dato de frecuencia cardíaca
     * @param heartRateData Dato a insertar
     * @return ID del dato insertado
     */
    public long insertHeartRateData(HeartRateData heartRateData) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_SESSION_ID_FK, heartRateData.getSessionId());
        values.put(COLUMN_TIMESTAMP, heartRateData.getTimestamp());
        values.put(COLUMN_HEART_RATE, heartRateData.getHeartRate());
        
        if (heartRateData.getRrInterval() != null) {
            values.put(COLUMN_RR_INTERVAL, heartRateData.getRrInterval());
        }
        
        long id = db.insert(TABLE_HEART_RATE_DATA, null, values);
        db.close();
        
        return id;
    }
    
    /**
     * Inserta múltiples datos de frecuencia cardíaca
     * @param dataList Lista de datos a insertar
     */
    public void insertHeartRateDataBatch(List<HeartRateData> dataList) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        try {
            db.beginTransaction();
            
            for (HeartRateData data : dataList) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_SESSION_ID_FK, data.getSessionId());
                values.put(COLUMN_TIMESTAMP, data.getTimestamp());
                values.put(COLUMN_HEART_RATE, data.getHeartRate());
                
                if (data.getRrInterval() != null) {
                    values.put(COLUMN_RR_INTERVAL, data.getRrInterval());
                }
                
                db.insert(TABLE_HEART_RATE_DATA, null, values);
            }
            
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error al insertar datos en lote: " + e.getMessage());
        } finally {
            db.endTransaction();
            db.close();
        }
    }
    
    /**
     * Obtiene los datos de frecuencia cardíaca de una sesión
     * @param sessionId ID de la sesión
     * @return Lista de datos de frecuencia cardíaca
     */
    public List<HeartRateData> getHeartRateDataForSession(long sessionId) {
        List<HeartRateData> dataList = new ArrayList<>();
        
        String selectQuery = "SELECT * FROM " + TABLE_HEART_RATE_DATA + 
                             " WHERE " + COLUMN_SESSION_ID_FK + " = ?" +
                             " ORDER BY " + COLUMN_TIMESTAMP;
        
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, new String[] { String.valueOf(sessionId) });
        
        if (cursor.moveToFirst()) {
            do {
                HeartRateData data = new HeartRateData();
                data.setId(cursor.getLong(cursor.getColumnIndex(COLUMN_HR_ID)));
                data.setSessionId(cursor.getLong(cursor.getColumnIndex(COLUMN_SESSION_ID_FK)));
                data.setTimestamp(cursor.getLong(cursor.getColumnIndex(COLUMN_TIMESTAMP)));
                data.setHeartRate(cursor.getInt(cursor.getColumnIndex(COLUMN_HEART_RATE)));
                
                int rrColIndex = cursor.getColumnIndex(COLUMN_RR_INTERVAL);
                if (!cursor.isNull(rrColIndex)) {
                    data.setRrInterval(cursor.getInt(rrColIndex));
                }
                
                dataList.add(data);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        
        return dataList;
    }
    
    /**
     * Elimina todos los datos de frecuencia cardíaca de una sesión
     * @param sessionId ID de la sesión
     * @return Número de filas eliminadas
     */
    public int deleteHeartRateDataForSession(long sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        try {
            return db.delete(
                TABLE_HEART_RATE_DATA, 
                COLUMN_SESSION_ID_FK + " = ?", 
                new String[]{String.valueOf(sessionId)}
            );
        } catch (Exception e) {
            Log.e(TAG, "Error al eliminar datos de frecuencia cardíaca para sesión: " + sessionId, e);
            return 0;
        }
    }
    
    /**
     * Recupera los intervalos RR para una sesión específica
     * @param sessionId ID de la sesión
     * @return Lista de intervalos RR en ms
     */
    public List<Integer> getRRIntervalsForSession(long sessionId) {
        List<Integer> rrIntervals = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query = "SELECT " + COLUMN_RR_INTERVAL + 
                       " FROM " + TABLE_HEART_RATE_DATA + 
                       " WHERE " + COLUMN_SESSION_ID_FK + " = ? AND " + 
                       COLUMN_RR_INTERVAL + " IS NOT NULL AND " + 
                       COLUMN_RR_INTERVAL + " > 0" +
                       " ORDER BY " + COLUMN_TIMESTAMP;
        
        Cursor cursor = null;
        
        try {
            cursor = db.rawQuery(query, new String[]{String.valueOf(sessionId)});
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int rrInterval = cursor.getInt(cursor.getColumnIndex(COLUMN_RR_INTERVAL));
                    rrIntervals.add(rrInterval);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener intervalos RR para sesión: " + sessionId, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return rrIntervals;
    }
} 