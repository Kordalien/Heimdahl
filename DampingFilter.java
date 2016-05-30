/**
 * Created by serenity on 5/12/16.
 */
public class DampingFilter implements Filter{
  public double [] process(double [] md, String [] args){
      double scale=1;
      double maxAmp=1;
      double period=500;
      if(args.length>0)
          scale = Double.parseDouble(args[0]);
      if(args.length>1)
          scale = Double.parseDouble(args[1]);
      if(args.length>2)
          scale = Double.parseDouble(args[2]);
      for (int i=1; i< md.length; i++){
          int sig = (int)Math.signum(md[i]);
          md[i]=Math.max(Math.min(((Math.exp(-scale*Math.abs(md[i]+1))-1)*2+1)*maxAmp,1),-1)*Math.abs(Math.sin(Math.PI/period*(i%period)));

          //md[i] = Math.sin((i%440)*Math.PI*2/440);
          //md[i] = md[i]-md[i-1];
      }
        return md;
    }
}
