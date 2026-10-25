from pathlib import Path
import subprocess,sys
p=Path(sys.argv[1]);src=p.read_text();expected=int(sys.argv[2]);out=Path(sys.argv[3]);out.mkdir(parents=True,exist_ok=True)
start=src.index('            // module clicks that land between frames still count') if '            // module clicks that land between frames still count' in src else src.index('            // Count every queued click')
end=src.index('            leftWas = lmb;',start)
body=src[start:end].replace('com.utilitymod.echo.handlers.InputHandler.SIMULATED_CLICKS','queue')
java='import java.util.*; public class CounterTest { static ArrayDeque<int[]> queue=new ArrayDeque<>(); static ArrayDeque<Long> leftClicks=new ArrayDeque<>(),rightClicks=new ArrayDeque<>(); static boolean leftWas,rightWas; static int cps(ArrayDeque<Long> q,boolean d,boolean w){if(d&&!w)q.addLast(0L);return q.size();} static void frame(boolean lmb,boolean rmb){'+body+'leftWas=lmb;rightWas=rmb;} public static void main(String[] a){ for(int t=0;t<20;t++){for(int i=0;i<5;i++)queue.add(new int[]{0});frame(true,false);frame(false,false);} if(leftClicks.size()!='+str(expected)+')throw new AssertionError(leftClicks.size()); System.out.println("PASS: queued 100 clicks across 20 ticks; counted "+leftClicks.size()); leftClicks.clear();rightClicks.clear();leftWas=false;rightWas=false;frame(true,true);if(leftClicks.size()!=1||rightClicks.size()!=1)throw new AssertionError();System.out.println("PASS: physical-only clicks retained"); }}'
(out/'CounterTest.java').write_text(java)
jdk=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot/bin')
subprocess.run([str(jdk/'javac.exe'),str(out/'CounterTest.java')],check=True)
subprocess.run([str(jdk/'java.exe'),'-cp',str(out),'CounterTest'],check=True)
