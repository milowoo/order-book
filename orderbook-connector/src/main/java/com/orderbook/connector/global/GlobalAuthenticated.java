package com.orderbook.connector.global;

import com.orderbook.connector.common.dto.*;
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
public interface GlobalAuthenticated {

    @GET
    @Path("api/v2/spot/account/assets")
    BitgetResponse<List<BitgetBalanceDto>> balances(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("coin") String currency)
            throws IOException, BitgetException;

    @GET
    @Path("api/v2/spot/account/subaccount-assets")
    BitgetResponse<List<BitgetSubBalanceDto>> subBalances(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading)
            throws IOException, BitgetException;

    @GET
    @Path("api/v2/spot/trade/orderInfo")
    BitgetResponse<List<BitgetOrderInfoDto>> orderInfo(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("orderId") String orderId)
            throws IOException, BitgetException;

    @POST
    @Path("api/v2/spot/trade/place-order")
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
    @Path("api/v2/spot/trade/cancel-order")
    @Consumes(MediaType.APPLICATION_JSON)
    BitgetResponse<BitgetOrderInfoDto> cancelOrder(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            BitgetCancelOrderDto bitgetCancelOrderDto)
            throws IOException, BitgetException;

    @POST
    @Path("api/v2/spot/trade/cancel-symbol-order")
    @Consumes(MediaType.APPLICATION_JSON)
    BitgetResponse<SpotCancelOrderBySymbolResult> cancelSymbolOrder(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            SpotCancelOrderBySymbolDto spotCancelOrderSymbolDto)
            throws IOException, BitgetException;

    @POST
    @Path("api/v2/spot/trade/batch-cancel-order")
    @Consumes(MediaType.APPLICATION_JSON)
    BitgetResponse<SpotOrderBatchResult> cancelBatchOrder(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            SpotCancelBatchOrderDTO spotCancelBatchOrderDTO)
            throws IOException, BitgetException;

    @POST
    @Path("api/v2/spot/trade/batch-orders")
    @Consumes(MediaType.APPLICATION_JSON)
    BitgetResponse<SpotOrderBatchResult> batchOrders(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            SpotBatchOrdersDto spotBatchOrdersDto)
            throws IOException, BitgetException;

    @GET
    @Path("api/v2/spot/trade/unfilled-orders")
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
    @Path("api/v2/spot/trade/fills")
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
    @Path("api/v2/spot/account/transferRecords")
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
    @Path("api/v2/spot/account/sub-main-trans-record")
    BitgetResponse<List<BitgetMainSubTransferRecordDto>> mainSubTransferRecords(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("coin") String currency,
            @QueryParam("limit") Integer limit,
            @QueryParam("clientOid") String clientOid,
            @QueryParam("subAccountUid") String subAccountUid,
            @QueryParam("startTime") Long startTime,
            @QueryParam("endTime") Long endTime,
            @QueryParam("idLessThan") String idLessThan)
            throws IOException, BitgetException;

    @GET
    @Path("api/v2/spot/wallet/deposit-records")
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
    @Path("api/v2/spot/wallet/subaccount-deposit-records")
    BitgetResponse<List<BitgetDepositWithdrawRecordDto>> subDepositRecords(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            @QueryParam("coin") String currency,
            @QueryParam("limit") Integer limit,
            @QueryParam("subAccountUid") String subAccountUid,
            @QueryParam("startTime") Long startTime,
            @QueryParam("endTime") Long endTime,
            @QueryParam("idLessThan") String idLessThan)
            throws IOException, BitgetException;

    @GET
    @Path("api/v2/spot/wallet/withdrawal-records")
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

    /**
     * 调整盘口
     * Returns:
     */
    @POST
    @Path("api/spot/v1/trade/changeDepth")
    @Consumes(MediaType.APPLICATION_JSON)
    BitgetResponse<SpotModifyDepthVo> changeDepth(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            SpotModifyDepthParam body)
            throws IOException, BitgetException;

    @POST
    @Path("api/spot/v1/trade/fills")
    @Consumes(MediaType.APPLICATION_JSON)
    BitgetResponse<List<SpotFillsOrderResult>> fillsV1(
            @HeaderParam("ACCESS-KEY") String apiKey,
            @HeaderParam("ACCESS-SIGN") ParamsDigest signer,
            @HeaderParam("ACCESS-PASSPHRASE") String passphrase,
            @HeaderParam("ACCESS-TIMESTAMP") SynchronizedValueFactory<Long> timestamp,
            @HeaderParam("paptrading") Integer paptrading,
            SpotFillsOrderDTO body)
            throws IOException, BitgetException;
}