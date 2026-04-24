package io.github.evoschema.config;

import com.atomikos.icatch.jta.UserTransactionImp;
import com.atomikos.icatch.jta.UserTransactionManager;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.jta.JtaTransactionManager;

@Configuration
@ConditionalOnProperty(name = "evoschema.tx.useAtomikos", havingValue = "true")
public class AtomikosJtaConfig
{
    @Bean(name = "userTransaction")
    public UserTransaction userTransaction() throws Exception
    {
        UserTransactionImp userTransaction = new UserTransactionImp();
        userTransaction.setTransactionTimeout(3000);
        return userTransaction;
    }

    @Bean(name = "userTransactionManager", initMethod = "init", destroyMethod = "close")
    public TransactionManager userTransactionManager()
    {
        UserTransactionManager userTransactionManager = new UserTransactionManager();
        userTransactionManager.setForceShutdown(false);
        return userTransactionManager;
    }

    @Bean(name = "transactionManager")
    public JtaTransactionManager transactionManager(UserTransaction userTransaction, TransactionManager transactionManager)
    {
        return new JtaTransactionManager(userTransaction, transactionManager);
    }
}
