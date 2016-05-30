/**Filter
 * The filter interface defines the fundamental tools needed
 * to filter a piece of music. In particular it offers a singular interface process
 * which takes as an input the music data and a set of arguments
 * (as an array of Objects possibly, depends on how robust we want it to be)
 *
 * This returns the musicdata post processing.
 * Created by serenity on 4/28/16.
 */
public interface Filter {
    public double[] process(double [] musicData, String [] arg);
}
