package com.uws.transaction_service.service;

import com.uws.transaction_service.model.Transaction;

 public  interface TransactionOrchestratorI {
      void initiateTransaction(Transaction transaction);

      void completeTransaction(Transaction transaction);

      void senderDebit(Transaction transaction,String walletId);

      void triggerFraudCheck(Transaction transaction);

      void creditReceiver(Transaction transaction);

     void failTransaction(Transaction transaction, String reason);

      void compensateTransaction(Transaction transaction, String reason);

     void directCredit(Transaction transaction);
}
