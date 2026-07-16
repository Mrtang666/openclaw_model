package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class WeixinMessage {
    @JsonProperty("from_user_id")
    private String fromUserId;
    @JsonProperty("to_user_id")
    private String toUserId;
    @JsonProperty("message_id")
    private Long messageId;
    @JsonProperty("client_id")
    private String clientId;
    @JsonProperty("message_type")
    private MessageType messageType;
    @JsonProperty("message_state")
    private MessageState messageState;
    @JsonProperty("context_token")
    private String contextToken;
    @JsonProperty("item_list")
    private List<MessageItem> itemList;

    public WeixinMessage() {}

    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }
    public String getToUserId() { return toUserId; }
    public void setToUserId(String toUserId) { this.toUserId = toUserId; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
    public MessageState getMessageState() { return messageState; }
    public void setMessageState(MessageState messageState) { this.messageState = messageState; }
    public String getContextToken() { return contextToken; }
    public void setContextToken(String contextToken) { this.contextToken = contextToken; }
    public List<MessageItem> getItemList() { return itemList; }
    public void setItemList(List<MessageItem> itemList) { this.itemList = itemList; }
}
