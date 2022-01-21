package nablarch.test.junit5.extension.http;

import nablarch.test.core.db.DbInfo;
import nablarch.test.core.db.TableData;
import nablarch.test.core.file.DataFile;
import nablarch.test.core.messaging.MessagePool;
import nablarch.test.core.reader.TestDataParser;
import nablarch.test.core.reader.TestDataReader;
import nablarch.test.core.util.interpreter.TestDataInterpreter;

import java.util.List;
import java.util.Map;

/**
 * テスト用の {@link TestDataParser} のモック。
 */
public class MockTestDataParser implements TestDataParser {
    @Override
    public List<TableData> getExpectedTableData(String path, String resourceName, String... groupId) {
        return null;
    }

    @Override
    public List<TableData> getSetupTableData(String path, String resourceName, String... groupId) {
        return null;
    }

    @Override
    public List<Map<String, String>> getListMap(String path, String resourceName, String id) {
        return null;
    }

    @Override
    public List<DataFile> getSetupFile(String path, String resourceName, String... groupId) {
        return null;
    }

    @Override
    public List<DataFile> getExpectedFile(String path, String resourceName, String... groupId) {
        return null;
    }

    @Override
    public MessagePool getMessage(String path, String resourceName, String id) {
        return null;
    }

    @Override
    public void setTestDataReader(TestDataReader testDataReader) {

    }

    @Override
    public void setDbInfo(DbInfo dbInfo) {

    }

    @Override
    public void setInterpreters(List<TestDataInterpreter> interpreter) {

    }

    @Override
    public boolean isResourceExisting(String basePath, String resourceName) {
        return false;
    }
}
