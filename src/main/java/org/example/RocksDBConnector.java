package org.example;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.RestExpress;
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 
 The given code block contains a simple counter using rocksdb's merge operator
 
 the Counters Fails for the following Cases
 
 $ ab -r -k -n 10000 -c 1000 http://localhost:9009/increment

 $ curl http://localhost:9009/get                                                                                                                                                           

 Current RocksDB Count is 137   (Expected value is 10000)
 Current AtomicLong Count is 10000

 $ curl http://localhost:9009/reset

 Current RocksDB Count is 0
 Current AtomicLong Count is 0

 $ ab -r -k -n 10000 -c 1000 http://localhost:9009/batchIncrement

 $ curl http://localhost:9009/get

 Current RocksDB Count is 16   (Expected value is 10000)
 Current AtomicLong Count is 10000
 
 */
public class RocksDBConnector {
    private static final Logger LOG = LoggerFactory.getLogger(RocksDBConnector.class);
    private  static  RocksDB db;
    private static List<ColumnFamilyDescriptor> colFamily= new ArrayList<ColumnFamilyDescriptor>();
    private static List<ColumnFamilyHandle> colFamilyHandles= new ArrayList<ColumnFamilyHandle>();
    private final static String KEY = "testKey";
    private final static byte[] KEY_BYTES = KEY.getBytes();
    private final static byte[] BYTES_1 = util.longToByte(1L);
    private final static byte[] BYTES_0 = util.longToByte(0L);
    private static final Cache<String,String> guavaCachedIndex = CacheBuilder.newBuilder().build();
    private  RocksDBConnector(){}
    private static final String SERVICE_NAME = "Admin Service";
    private static final int DEFAULT_EXECUTOR_THREAD_POOL_SIZE = 2;
    private static final int SERVER_PORT = 9009;
    private final static RocksDBConnector instance = new RocksDBConnector();
    private static AtomicLong simpleCounter = new AtomicLong();
    private static AtomicLong batchCounter = new AtomicLong();


    public static void main(String[] args) {
        RestExpress server = null;
        try {
            initializeRocksDb();
            server = initializeServer(args);
            server.awaitShutdown();
        } catch (IOException e) {
            System.out.print(e);
            LOG.info(e.getMessage());
        } catch (RocksDBException e) {
            System.out.print(e);
            LOG.info(e.getMessage());
        }
    }
        
    private static RestExpress initializeServer(String[] args) throws IOException {
        RestExpress server = new RestExpress()
                .setName(SERVICE_NAME)
                .setBaseUrl("http://localhost:" + SERVER_PORT)
                .setExecutorThreadCount(DEFAULT_EXECUTOR_THREAD_POOL_SIZE);

        server.uri("/increment",instance).action("incrementCounter", HttpMethod.GET).noSerialization();
        server.uri("/incrementAtomic",instance).action("incrementAtomicLong", HttpMethod.GET).noSerialization();
        server.uri("/simpleRead",instance).action("simpleReadRocksDb", HttpMethod.GET).noSerialization();
        server.uri("/simpleReadGuava",instance).action("simpleReadGuava", HttpMethod.GET).noSerialization();
        server.uri("/incrementAtomic",instance).action("incrementAtomicLong", HttpMethod.GET).noSerialization();
        server.uri("/reset",instance).action("resetCounterValue", HttpMethod.GET).noSerialization();
        server.uri("/get",instance).action("getCounterValue", HttpMethod.GET).noSerialization();
        server.bind(SERVER_PORT);
        return server;
    }
    private  static void initializeRocksDb() throws RocksDBException {
            colFamily.add(new ColumnFamilyDescriptor("default".getBytes()));
            colFamily.add(new ColumnFamilyDescriptor("testing2".getBytes()));
            colFamily.add(new ColumnFamilyDescriptor("testing1".getBytes()));
            RocksDB.loadLibrary();
            DBOptions options = new DBOptions().setCreateIfMissing(true);
//            options.setMergeOperatorName("uint64add");
//            options.setMaxBackgroundFlushes(1);
//            options.setWriteBufferSize(50L);
            // TODO:: please set hdfs url
//            options.setEnv( new HdfsEnv(""));
            
            options.setCreateMissingColumnFamilies(true);
            if (db == null) {
                db = RocksDB.open(options, "./rocksdb_data/testdata", colFamily, colFamilyHandles);
            }
        
        db.put(colFamilyHandles.get(1),KEY_BYTES,KEY_BYTES);
        guavaCachedIndex.put(KEY,KEY);
    }

    private static Long getCount(){
        try {
            return util.byteToLong(db.get(KEY_BYTES));
        } catch (RocksDBException e) {
            LOG.debug(e.getMessage());
            return 0L;
        }
    }
    private  static void resetCounter(){
        try {
            db.put(colFamilyHandles.get(0), KEY_BYTES, BYTES_0);
            
        } catch (RocksDBException e) {
            //LOG.debug(e.getMessage());
        }
    }
    private static String getFromRocks(){
        String a ;
        try {
            a = new String(db.get(colFamilyHandles.get(1), KEY_BYTES));
        } catch (RocksDBException e) {
            a=null;
        }
        return a;
    }
    private static String getFromGuava(){
        return guavaCachedIndex.getIfPresent(KEY);
    }
    private  static void mergeOperaton(){
        try {
            db.merge(colFamilyHandles.get(0), KEY_BYTES, BYTES_1);

        } catch (RocksDBException e) {
            LOG.debug(e.getMessage());
        }
    }

    private static void mergeBatchOperation(){
        WriteBatch batch = new WriteBatch();
        WriteOptions write_option = new WriteOptions();
        try {
            batch.merge(colFamilyHandles.get(0), KEY_BYTES, BYTES_1);
            db.write(write_option,batch);

        }catch (Exception e){
            LOG.debug(e.getMessage());
        }
    }
    public  void incrementAtomicLong(Request request,Response response){
        simpleCounter.incrementAndGet();
    }
    public  void simpleReadGuava(Request request,Response response){
        response.setBody(getFromGuava());
    }
    public  void simpleReadRocksDb(Request request,Response response){
        response.setBody(getFromRocks());
    }
    
    
    public void incrementCounter(Request request, Response response) {
        mergeOperaton();
    }
    public void batchIncrementCounter(Request request, Response response) {
        mergeBatchOperation();
    }
    
    public void getCounterValue(Request request, Response response) {
            response.setBody("Current RocksDB Count is "+getCount()+"\n"+"Current AtomicLong Count is "+simpleCounter.get());
    }
    public void resetCounterValue(Request request, Response response) {
        simpleCounter.set(0L);
        resetCounter();
        response.setBody("Current RocksDB Count is "+getCount()+"\n"+"Current AtomicLong Count is "+simpleCounter.get());
    }
}