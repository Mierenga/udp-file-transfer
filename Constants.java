public class Constants {

    private Constants(){}
    
    public static final int NO_RESPONSE_TIMEOUT = 5000;
    public static final int DATA_SIZE           = 1000;
    public static final int HEAD_SIZE           = 4;
    public static final int PACK_SIZE           = DATA_SIZE + HEAD_SIZE;
    public static final int WINDOW_SIZE         = 5;
    public static final int ACKD                = -1;
    public static final int EMPTY               = -2;
    public static final int ACK_TIMEOUT         = 100; // in millis

}
