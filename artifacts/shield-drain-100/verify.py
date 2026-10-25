from pathlib import Path
import re,subprocess,sys
src=Path(sys.argv[1]).read_text();expected=int(sys.argv[2]);out=Path(sys.argv[3]);out.mkdir(parents=True,exist_ok=True)
cps=int(re.search(r'shieldDrainCps = new IntSetting\(LT.L5, (\d+),',src)[1])
method=re.search(r'    private int drainBurst\(int tick\) \{.*?\n    \}',src,re.S)[0]
java='public class DrainRateTest { double drainAccum; int lastDrainTick=-1; static class Setting { int getValue(){return '+str(cps)+';} } Setting shieldDrainCps=new Setting();'+method+'public static void main(String[] a){ DrainRateTest x=new DrainRateTest(); int sum=0; for(int t=0;t<20;t++){int n=x.drainBurst(t); if(n>5)throw new AssertionError(); sum+=n; if(x.drainBurst(t)!=0)throw new AssertionError();} if(sum!='+str(expected)+')throw new AssertionError(sum); System.out.println("PASS: "+sum+" attempts / 20 ticks; max 5 per tick; duplicate ticks blocked"); }}'
(out/'DrainRateTest.java').write_text(java)
jdk=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot/bin')
subprocess.run([str(jdk/'javac.exe'),str(out/'DrainRateTest.java')],check=True)
subprocess.run([str(jdk/'java.exe'),'-cp',str(out),'DrainRateTest'],check=True)
