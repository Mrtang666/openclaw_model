package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageItem {
    private MessageItemType type;
    @JsonProperty("text_item")
    private TextItem textItem;
    @JsonProperty("image_item")
    private ImageItem imageItem;
    @JsonProperty("voice_item")
    private VoiceItem voiceItem;
    @JsonProperty("video_item")
    private VideoItem videoItem;
    @JsonProperty("file_item")
    private FileItem fileItem;
    @JsonProperty("ref_message")
    private RefMessage refMessage;

    public MessageItem() {}

    public MessageItemType getType() { return type; }
    public void setType(MessageItemType type) { this.type = type; }
    public TextItem getTextItem() { return textItem; }
    public void setTextItem(TextItem textItem) { this.textItem = textItem; }
    public ImageItem getImageItem() { return imageItem; }
    public void setImageItem(ImageItem imageItem) { this.imageItem = imageItem; }
    public VoiceItem getVoiceItem() { return voiceItem; }
    public void setVoiceItem(VoiceItem voiceItem) { this.voiceItem = voiceItem; }
    public VideoItem getVideoItem() { return videoItem; }
    public void setVideoItem(VideoItem videoItem) { this.videoItem = videoItem; }
    public FileItem getFileItem() { return fileItem; }
    public void setFileItem(FileItem fileItem) { this.fileItem = fileItem; }
    public RefMessage getRefMessage() { return refMessage; }
    public void setRefMessage(RefMessage refMessage) { this.refMessage = refMessage; }
}
