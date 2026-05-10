package com.orderbook.connector.global.service;

import com.orderbook.connector.global.GlobalErrorAdapter;
import com.orderbook.connector.global.GlobalAdapters;
import com.orderbook.connector.global.GlobalExchange;
import org.knowm.xchange.bitget.dto.BitgetException;
import org.knowm.xchange.bitget.dto.account.BitgetBalanceDto;
import org.knowm.xchange.bitget.service.params.BitgetFundingHistoryParams;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GlobalAccountService extends GlobalAccountServiceRaw implements AccountService {

    public GlobalAccountService(GlobalExchange exchange) {
        super(exchange);
    }

    @Override
    public AccountInfo getAccountInfo() throws IOException {
        try {
            List<BitgetBalanceDto> spotBalances = getBitgetBalances(null);
            Wallet wallet = GlobalAdapters.toWallet(spotBalances);
            return new AccountInfo(wallet);
        } catch (BitgetException e) {
            throw GlobalErrorAdapter.adapt(e);
        }
    }

    @Override
    public TradeHistoryParams createFundingHistoryParams() {
        return BitgetFundingHistoryParams.builder().build();
    }

    @Override
    public List<FundingRecord> getFundingHistory(TradeHistoryParams params) throws IOException {
        return Stream.of(getBitgetDepositRecords(params), getBitgetWithdrawRecords(params))
                .flatMap(List::stream)
                .map(GlobalAdapters::toFundingRecord)
                .collect(Collectors.toList());
    }
}