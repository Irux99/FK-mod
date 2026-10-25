"""Blender CLI: orient supplied OBJ along +Z and retain its UV coordinates."""
import bpy,numpy as np,json,sys
from mathutils import Vector
args=sys.argv[sys.argv.index('--')+1:]
from pathlib import Path
Path(args[1]).parent.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True);bpy.ops.wm.obj_import(filepath=args[0])
objects=[o for o in bpy.data.objects if o.type=='MESH']
ps=np.array([tuple(o.matrix_world@v.co) for o in objects for v in o.data.vertices]);center=ps.mean(axis=0)
values,axes=np.linalg.eigh(np.cov((ps-center).T));axis=axes[:,2];projected=(ps-center)@axis
radius=np.linalg.norm((ps-center)-projected[:,None]*axis,axis=1)
if radius[projected>np.quantile(projected,.9)].mean()>radius[projected<np.quantile(projected,.1)].mean():axis=-axis
up=np.array([0.,1.,0.]);right=np.cross(up,axis)
if np.linalg.norm(right)<.1:right=np.cross(np.array([1.,0.,0.]),axis)
right/=np.linalg.norm(right);up=np.cross(axis,right);matrix=np.array([right,up,axis]);scale=2.1/np.ptp((ps-center)@axis)
vertices=[]
for o in objects:
 o.data.calc_loop_triangles();uv=o.data.uv_layers.active
 for t in o.data.loop_triangles:
  n=matrix@np.array(o.matrix_world.to_3x3()@t.normal);n/=max(np.linalg.norm(n),1e-8)
  for vi,li in zip(t.vertices,t.loops):
   p=(matrix@(np.array(o.matrix_world@o.data.vertices[vi].co)-center))*scale
   tex=uv.data[li].uv if uv else (.5,.5)
   vertices.append([*[round(float(a),6) for a in p],*[round(float(a),5) for a in n],1.,1.,1.,float(tex[0]),1.-float(tex[1])])
with open(args[1],'w') as f:json.dump({'format':1,'triangles':len(vertices)//3,'vertices':vertices},f,separators=(',',':'))
print('EXPORTED MISSILE',len(vertices)//3,'triangles')
