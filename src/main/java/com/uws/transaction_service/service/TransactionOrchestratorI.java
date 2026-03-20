package com.uws.transaction_service.service;

import com.uws.transaction_service.model.Transaction;

 public  interface TransactionOrchestratorI {
      void completeTransaction(Transaction transaction);

      void triggerFraudCheck(Transaction transaction);

      void creditReceiver(Transaction transaction);

      void compensateTransaction(Transaction transaction, String reason);
}
