package com.orderbook.connector.bitget;

import com.orderbook.connector.common.dto.BitgetCancelOrderDto;
import com.orderbook.connector.common.dto.BitgetPlaceOrderDto;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.knowm.xchange.bitget.dto.BitgetException;
import org.knowm.xchange.bitget.dto.BitgetResponse;
import org.knowm.xchange.bitget.dto.account.*;
import org.knowm.xchange.bitget.dto.trade.BitgetFillDto;
import org.knowm.xchange.bitget.dto.trade.BitgetOrderInfoDto;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

import java.io.IOException;
import java.util.List;

@Path("")
@Produces(MediaType.APPLICATION_JSON)
public interface BitgetAuthenticated {

    @GET
    @Path("/api/v2/spot/account/assets")
    BitgetResponse<List<BitgetBalanceDto>> balances(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("coin") String currency)
            throws IOException, BitgetException;

    @GET
    @Path("/api/v2/spot/account/subaccount-assets")
    BitgetResponse<List<BitgetSubBalanceDto>> subBalances(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading)
            throws IOException, BitgetException;

    @GET
    @Path("/api/v2/spot/trade/orderInfo")
    BitgetResponse<List<BitgetOrderInfoDto>> orderInfo(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("orderId") String orderId)
            throws IOException, BitgetException;

    @POST
    @Path("/api/v2/spot/trade/place-order")
    @Consumes(MediaType.APPLICATION_JSON)
    BitgetResponse<BitgetOrderInfoDto> createOrder(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            BitgetPlaceOrderDto bitgetPlaceOrderDto)
            throws IOException, BitgetException;

    @POST
    @Path("/api/v2/spot/trade/cancel-order")
    @Consumes(MediaType.APPLICATION_JSON)
    BitgetResponse<BitgetOrderInfoDto> cancelOrder(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            BitgetCancelOrderDto bitgetCancelOrderDto)
            throws IOException, BitgetException;

    @GET
    @Path("/api/v2/spot/trade/unfilled-orders")
    BitgetResponse<List<BitgetOrderInfoDto>> getOpenOrders(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("symbol") String symbol,
            @QueryParam("limit") Integer limit,
            @QueryParam("orderId") String orderId,
            @QueryParam("startTime") Long startTime,
            @QueryParam("endTime") Long endTime,
            @QueryParam("idLessThan") String idLessThan,
            @QueryParam("tpslType") String tpslType,
            @QueryParam("requestTime") String requestTime,
            @QueryParam("receiveWindow") String receiveWindow)
            throws IOException, BitgetException;

    @GET
    @Path("/api/v2/spot/trade/fills")
    BitgetResponse<List<BitgetFillDto>> fills(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("symbol") String symbol,
            @QueryParam("limit") Integer limit,
            @QueryParam("orderId") String orderId,
            @QueryParam("startTime") Long startTime,
            @QueryParam("endTime") Long endTime,
            @QueryParam("idLessThan") String idLessThan)
            throws IOException, BitgetException;

    @GET
    @Path("/api/v2/spot/account/transferRecords")
    BitgetResponse<List<BitgetTransferRecordDto>> transferRecords(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("coin") String currency,
            @QueryParam("limit") Integer limit,
            @QueryParam("clientOid") String clientOid,
            @QueryParam("fromType") String fromType,
            @QueryParam("startTime") Long startTime,
            @QueryParam("endTime") Long endTime,
            @QueryParam("idLessThan") String idLessThan)
            throws IOException, BitgetException;

    @GET
    @Path("/api/v2/spot/account/sub-main-trans-record")
    BitgetResponse<List<BitgetMainSubTransferRecordDto>> mainSubTransferRecords(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("coin") String currency,
            @QueryParam("limit") Integer limit,
            @QueryParam("clientOid") String clientOid,
            @QueryParam("role") String role,
            @QueryParam("subUid") String subAccountUid,
            @QueryParam("startTime") Long startTime,
            @QueryParam("endTime") Long endTime,
            @QueryParam("idLessThan") String idLessThan)
            throws IOException, BitgetException;

    @GET
    @Path("/api/v2/spot/wallet/deposit-records")
    BitgetResponse<List<BitgetDepositWithdrawRecordDto>> depositRecords(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("coin") String currency,
            @QueryParam("limit") Integer limit,
            @QueryParam("orderId") String orderId,
            @QueryParam("startTime") Long startTime,
            @QueryParam("endTime") Long endTime,
            @QueryParam("idLessThan") String idLessThan)
            throws IOException, BitgetException;

    @GET
    @Path("/api/v2/spot/wallet/subaccount-deposit-records")
    BitgetResponse<List<BitgetDepositWithdrawRecordDto>> subDepositRecords(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("coin") String currency,
            @QueryParam("limit") Integer limit,
            @QueryParam("subUid") String subAccountUid,
            @QueryParam("startTime") Long startTime,
            @QueryParam("endTime") Long endTime,
            @QueryParam("idLessThan") String idLessThan)
            throws IOException, BitgetException;

    @GET
    @Path("/api/v2/spot/wallet/withdrawal-records")
    BitgetResponse<List<BitgetDepositWithdrawRecordDto>> withdrawalRecords(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("coin") String currency,
            @QueryParam("limit") Integer limit,
            @QueryParam("orderId") String orderId,
            @QueryParam("clientOid") String clientOid,
            @QueryParam("startTime") Long startTime,
            @QueryParam("endTime") Long endTime,
            @QueryParam("idLessThan") String idLessThan)
            throws IOException, BitgetException;
}