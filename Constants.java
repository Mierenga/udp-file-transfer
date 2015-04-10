public class Constants {

    private Constants(){}
    
    public static final int NO_RESPONSE_TIMEOUT = 100;
    public static final int DATA_SIZE           = 1000;
    public static final int SEQ_SIZE            = 4; // TODO adjust properly
    public static final int SUM_SIZE            = 4;
    public static final int PACK_SIZE           = DATA_SIZE + SEQ_SIZE + SUM_SIZE;
    public static final int WINDOW_SIZE         = 5;
    public static final int ACKD                = -1;
    public static final int EMPTY               = -2;
    public static final int ACK_TIMEOUT         = 5; // in millis

}
