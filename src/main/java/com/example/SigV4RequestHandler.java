package com.example;

public class SigV4RequestHandler {
}
//class SigV4RequestHandler {
//    private static final String SIGNING_NAME = "aps";
//    private static final SdkHttpClient httpClient = ApacheHttpClient.builder()
//            .socketTimeout(Duration.ofSeconds(5))
//            .connectionTimeout(Duration.ofSeconds(2))
//            .build();
//
//    private final Region region;
//    private final AwsCredentialsProvider credentialsProvider;
//
//    public SigV4RequestHandler(String region, String roleArn) {
//        this.region = Region.of(region);
//        this.credentialsProvider = getCredentialsProvider(roleArn);
//    }
//
//    public HttpExecuteResponse send(URI uri, byte[] payload) throws Exception {
//    }
//
//
//    public AwsCredentialsProvider getCredentialsProvider(String roleArn) {
//        if (roleArn != null && !roleArn.isEmpty()) {
//            StsClient stsClient = StsClient.builder()
//                    .region(region)
//                    .credentialsProvider(DefaultCredentialsProvider.create())
//                    .build();
//
//            return StsAssumeRoleCredentialsProvider.builder()
//                    .stsClient(stsClient)
//                    .refreshRequest(AssumeRoleRequest.builder()
//                            .roleArn(roleArn)
//                            .roleSessionName("prometheus-session")
//                            .build())
//                    .build();
//        }
//        return DefaultCredentialsProvider.create();
//    }
//}