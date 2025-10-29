package screentimeai.infra.event;

//<<< Clean Arch / Outbound Adaptor
public class AbstractEvent {

//    String eventType;
//    Long timestamp;
//
//    public AbstractEvent(Object aggregate) {
//        this();
//        BeanUtils.copyProperties(aggregate, this);
//    }
//
//    public AbstractEvent() {
//        this.setEventType(this.getClass().getSimpleName());
//        this.timestamp = System.currentTimeMillis();
//    }
//
//    public void publish() {
//        /**
//         * spring streams 방식
//         */
//        KafkaProcessor processor = InsightserviceApplication.applicationContext.getBean(
//            KafkaProcessor.class
//        );
//        MessageChannel outputChannel = processor.outboundTopic();
//
//        outputChannel.send(
//            MessageBuilder
//                .withPayload(this)
//                .setHeader(
//                    MessageHeaders.CONTENT_TYPE,
//                    MimeTypeUtils.APPLICATION_JSON
//                )
//                .setHeader("type", getEventType())
//                .build()
//        );
//    }
//
//    public void publishAfterCommit() {
//        TransactionSynchronizationManager.registerSynchronization(
//            new TransactionSynchronization() {
//                @Override
//                public void afterCommit() {
//                    AbstractEvent.this.publish();
//                }
//            }
//        );
//    }
//
//    public String getEventType() {
//        return eventType;
//    }
//
//    public void setEventType(String eventType) {
//        this.eventType = eventType;
//    }
//
//    public Long getTimestamp() {
//        return timestamp;
//    }
//
//    public void setTimestamp(Long timestamp) {
//        this.timestamp = timestamp;
//    }
//
//    public boolean validate() {
//        return getEventType().equals(getClass().getSimpleName());
//    }
//
//    public String toJson() {
//        ObjectMapper objectMapper = new ObjectMapper();
//        String json = null;
//
//        try {
//            json = objectMapper.writeValueAsString(this);
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException("JSON format exception", e);
//        }
//
//        return json;
//    }
}
//>>> Clean Arch / Outbound Adaptor
