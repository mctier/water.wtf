from pathlib import Path
import json,time,statistics,hashlib
import moderngl
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[3]
OUT=Path(__file__).resolve().parent
SRC=ROOT/'src/main/resources/assets/utilitymod/shaders/glass'
ctx=moderngl.create_standalone_context(require=330)
report={'gpu':ctx.info['GL_RENDERER'],'vendor':ctx.info['GL_VENDOR'],'version':ctx.info['GL_VERSION'],'scope':'Actual GLSL compiled and rendered in standalone WGL context; no Minecraft text/UI or Java GL-state integration executed.','tests':[]}
vs=(SRC/'lg_quad.vert').read_text()
base=(OUT/'baseline/lg_tinted.frag').read_text();modified=(SRC/'lg_tinted.frag').read_text()
programs={'baseline':ctx.program(vertex_shader=vs,fragment_shader=base),'legacy':ctx.program(vertex_shader=vs,fragment_shader=modified),'cards':ctx.program(vertex_shader=vs,fragment_shader=modified)}
for label,program in programs.items():
 report[label+'_compile']='PASS'

def run(label,w,h,bright=False):
 program=programs[label];pw,ph=int(w*.64),int(h*.74);px,py=(w-pw)//2,(h-ph)//2
 yy,xx=np.mgrid[:h,:w];checker=((xx//24+yy//24)%2).astype(np.float32)
 scene=np.empty((h,w,4),dtype=np.float32)
 if bright:scene[:,:,:3]=.92
 else:
  scene[:,:,0]=.18+.48*xx/w+.12*checker
  scene[:,:,1]=.22+.45*yy/h+.1*checker
  scene[:,:,2]=.33+.25*xx/w+.1*checker
 scene[:,:,3]=1
 texture=ctx.texture((w,h),4,scene.tobytes(),dtype='f4');texture.filter=(moderngl.LINEAR,moderngl.LINEAR);texture.use(0)
 target=ctx.texture((w,h),4,dtype='f4');fbo=ctx.framebuffer([target]);fbo.use();ctx.viewport=(0,0,w,h);ctx.disable(moderngl.BLEND);fbo.clear(0,0,0,0)
 values={'uScreen':(w,h),'uMid':(px+pw/2,py+ph/2),'uBlurTex':0,'uQuadSize':(pw,ph),'uRadius':22.,'uBlurLod':0.,'uNoise':0.,'uRefractionPower':.32,'uTintColor':(.80,.16,.66),'uTintStrength':.34,'uChromaStrength':.0008,'uDarkness':.02,'uAlpha':.92,'uCardMaterial':1. if label=='cards' else 0.}
 for k,v in values.items():
  if k in program:program[k].value=v
 data=np.array([[px,py,0,0],[px+pw,py,1,0],[px+pw,py+ph,1,1],[px,py,0,0],[px+pw,py+ph,1,1],[px,py+ph,0,1]],dtype='f4')
 vbo=ctx.buffer(data.tobytes());vao=ctx.vertex_array(program,[(vbo,'2f 2f','a_pos','a_uv')])
 for _ in range(8):vao.render()
 timings=[]
 for _ in range(20):
  with ctx.query(time=True) as query:vao.render()
  ctx.finish();timings.append(query.elapsed/1e6)
 image=np.frombuffer(fbo.read(components=4,dtype='f4'),dtype=np.float32).reshape(h,w,4).copy()
 assert np.isfinite(image).all(),(label,'nonfinite')
 assert float(image[0,0,3])==0,(label,'outside alpha')
 assert abs(float(image[py+ph//2,px+pw//2,3])-.92)<1e-4,(label,'interior alpha')
 assert float(image[py,px,3])<.01,(label,'round corner alpha')
 sample=lambda u,v:image[py+int(ph*v),px+int(pw*u),:3].tolist()
 rec={'label':label,'resolution':[w,h],'scene':'bright-flat' if bright else 'checker-gradient','gpu_panel_only_median_ms':statistics.median(timings),'gpu_panel_only_max_ms':max(timings),'top':sample(.25,.1),'lower_left':sample(.25,.82),'lower_right':sample(.92,.82),'alpha_center':float(image[py+ph//2,px+pw//2,3]),'finite':'PASS','rounded_mask':'PASS'}
 if label=='cards' and bright:
  assert rec['lower_left'][0]>rec['top'][0]+.1,'magenta pool absent'
  assert rec['lower_left'][0]>rec['lower_right'][0]+.15,'dark right gradient absent'
  rec['directional_gradient']='PASS'
 report['tests'].append(rec)
 if w==1280:
  rgba=np.uint8(np.clip(image,0,1)*255)
  Image.fromarray(rgba).save(OUT/(label+('-bright' if bright else '')+'.png'))
 vao.release();vbo.release();fbo.release();target.release();texture.release()
 return image
baseline=run('baseline',1280,720);legacy=run('legacy',1280,720)
legacy_error=float(np.max(np.abs(baseline-legacy)));assert legacy_error<1.0/255.0,legacy_error
report['legacy_max_absolute_rgba_delta']=legacy_error
run('cards',1280,720);run('cards',1280,720,True)
for w,h in [(854,480),(1920,1080),(2560,1440)]:run('cards',w,h,True)
report['shader_sha256']=hashlib.sha256(modified.encode()).hexdigest()
(OUT/'RESULTS.json').write_text(json.dumps(report,indent=2))
print('PASS: actual baseline and modified shaders compile/link on '+report['gpu'])
print('PASS: legacy output preserved, max RGBA difference '+str(legacy_error))
print('PASS: 4 resolutions, finite output, transparent outside/rounded corners, interior alpha, magenta pool and dark-right gradient')
for t in report['tests']:print(t['label'],t['resolution'],t['scene'],'GPU median ms',round(t['gpu_panel_only_median_ms'],4))


new=run('cards',1280,720)
programs['cards']=programs['baseline']
old=run('cards',1280,720)
# 30 GUI pixels inside each straight boundary excludes the 18px lens band and corners.
w,h=1280,720;pw,ph=int(w*.64),int(h*.74);px,py=(w-pw)//2,(h-ph)//2
interior=np.max(np.abs(new[py+30:py+ph-30,px+30:px+pw-30]-old[py+30:py+ph-30,px+30:px+pw-30]))
alpha=np.max(np.abs(new[:,:,3]-old[:,:,3]))
assert interior<1/255 and alpha==0
print('PASS: edge-only regression; interior max delta',float(interior),'alpha max delta',float(alpha))
programs['cards']=programs['legacy'];run('cards',1280,720)
