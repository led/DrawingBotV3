package drawingbot.utils;

/**
 * A lazy way to time things when debugging --todo use DATES?
 */
public class LazyTimer {

    public long startTime;
    public long endTime;


    public LazyTimer(){}

    /**
     * Also works as a reset
     */
    public void start(){
        startTime = System.currentTimeMillis();
    }

    public void finish(){
        endTime = System.currentTimeMillis();
    }

    public long getElapsedTime(){
        return endTime - startTime;
    }

    public String getElapsedTimeFormatted(){
        return getElapsedTimeFormatted(endTime - startTime);
    }
    /**
     * Prints out the finishing time
     */
    public static String getElapsedTimeFormatted(long elapsedTime){
        int seconds = (int) (elapsedTime / 1000) % 60 ;
        int minutes = (int) ((elapsedTime / (1000*60)) % 60);
        int hours = (int) ((elapsedTime / (1000*60*60)) % 24);

        if(hours == 0 && minutes == 0 && seconds == 0){
            return elapsedTime + " ms";
        }

        if(hours == 0 && minutes == 0){
            return seconds + " s";
        }

        if(hours == 0){
            return minutes + " m " + seconds + " s";
        }

        return hours + " h " + minutes + " m " + seconds + " s";
    }


}
