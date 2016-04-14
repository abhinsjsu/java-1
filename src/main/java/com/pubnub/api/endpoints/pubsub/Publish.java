package com.pubnub.api.endpoints.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pubnub.api.core.*;
import com.pubnub.api.core.enums.PNOperationType;
import com.pubnub.api.core.models.PublishData;
import com.pubnub.api.endpoints.Endpoint;
import lombok.Builder;
import retrofit2.Call;
import retrofit2.Response;

import java.util.List;
import java.util.Map;

@Builder
public class Publish extends Endpoint<List<Object>, PublishData> {

    private Pubnub pubnub;
    private Object message;
    private String channel;
    private Boolean shouldStore;
    private Boolean usePOST;
    private Object meta;

    @Override
    protected final boolean validateParams() {
        if (message == null) {
            return false;
        }

        if (channel == null || channel.length() == 0) {
            return false;
        }

        return true;
    }

    @Override
    protected final Call<List<Object>> doWork(Map<String, String> params) throws PubnubException {
        String stringifiedMessage;
        String stringifiedMeta;
        ObjectMapper mapper = new ObjectMapper();

        try {
            stringifiedMessage = mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new PubnubException(PubnubError.PNERROBJ_INVALID_ARGUMENTS, e.getMessage(), null);
        }

        if (meta != null) {
            try {
                stringifiedMeta = mapper.writeValueAsString(meta);
                stringifiedMeta = PubnubUtil.urlEncode(stringifiedMeta);
                params.put("meta", stringifiedMeta);
            } catch (JsonProcessingException e) {
                throw new PubnubException(PubnubError.PNERROBJ_INVALID_ARGUMENTS, e.getMessage(), null);
            }
        }

        if (pubnub.getConfiguration().getAuthKey() != null) {
            params.put("auth", pubnub.getConfiguration().getAuthKey());
        }

        params.put("uuid", pubnub.getConfiguration().getUuid());

        if (shouldStore != null && !shouldStore) {
            params.put("store", "0");
        }

        if (pubnub.getConfiguration().getCipherKey() != null) {
            Crypto crypto = new Crypto(pubnub.getConfiguration().getCipherKey());
            stringifiedMessage = crypto.encrypt(stringifiedMessage).replace("\n", "");
        }

        PubSubService service = this.createRetrofit(pubnub).create(PubSubService.class);

        if (usePOST != null && usePOST) {
            Object payloadToSend;

            if (pubnub.getConfiguration().getCipherKey() != null) {
                payloadToSend = stringifiedMessage;
            } else {
                payloadToSend = message;
            }

            return service.publishWithPost(pubnub.getConfiguration().getPublishKey(),
                    pubnub.getConfiguration().getSubscribeKey(),
                    channel, payloadToSend, params);
        } else {

            if (pubnub.getConfiguration().getCipherKey() != null) {
                stringifiedMessage = "\"".concat(stringifiedMessage).concat("\"");
            }

            stringifiedMessage = PubnubUtil.urlEncode(stringifiedMessage);

            return service.publish(pubnub.getConfiguration().getPublishKey(),
                    pubnub.getConfiguration().getSubscribeKey(),
                    channel, stringifiedMessage, params);
        }
    }

    @Override
    protected final PublishData createResponse(final Response<List<Object>> input) throws PubnubException {
        PublishData publishData = new PublishData();
        publishData.setTimetoken(Long.valueOf(input.body().get(2).toString()));

        return publishData;
    }

    protected int getConnectTimeout() {
        return pubnub.getConfiguration().getConnectTimeout();
    }

    protected int getRequestTimeout() {
        return pubnub.getConfiguration().getNonSubscribeRequestTimeout();
    }

    @Override
    protected PNOperationType getOperationType() {
        return PNOperationType.PNPublishOperation;
    }

    @Override
    protected Pubnub getPubnub() {
        return pubnub;
    }

}
