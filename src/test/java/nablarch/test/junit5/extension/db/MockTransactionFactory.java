package nablarch.test.junit5.extension.db;

import nablarch.core.transaction.Transaction;
import nablarch.core.transaction.TransactionFactory;

public class MockTransactionFactory implements TransactionFactory {
    @Override
    public Transaction getTransaction(String s) {
        return new Transaction() {
            @Override
            public void begin() {

            }

            @Override
            public void commit() {

            }

            @Override
            public void rollback() {

            }
        };
    }
}
