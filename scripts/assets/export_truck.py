"""Blender CLI: supplied Truck.fbx -> optimized chassis, 8 wheel joints and launcher.
Usage: blender -b --python export_truck.py -- INPUT.fbx OUTPUT.json
No Superb Warfare art is used. Paint colours are generated for this project.
"""
import bpy,json,sys,math,numpy as np
from mathutils import Vector
args=sys.argv[sys.argv.index('--')+1:]
from pathlib import Path
Path(args[1]).parent.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.fbx(filepath=args[0],use_image_search=False)
objects=[o for o in bpy.data.objects if o.type=='MESH']
points=[o.matrix_world@Vector(p) for o in objects for p in o.bound_box]
low=min(v.z for v in points);center=(min(v.y for v in points)+max(v.y for v in points))/2
scale=6.0/(max(v.y for v in points)-min(v.y for v in points))
def convert(p):return [p[0]*scale,(p[2]-low)*scale,-(p[1]-center)*scale]
wheel=next(o for o in objects if o.name=='Cube.002')
wp=np.array([tuple(wheel.matrix_world@v.co) for v in wheel.data.vertices]);ys=wp[:,1]
centers=np.quantile(ys,[.125,.375,.625,.875])
for _ in range(30):
 labels=np.argmin(abs(ys[:,None]-centers[None,:]),axis=1)
 centers=np.array([ys[labels==i].mean() for i in range(4)])
centers.sort();parts={}
def part(name,animation,pivot):
 if name not in parts:parts[name]={'name':name,'animation':animation,'pivot':convert(pivot) if animation else [0,0,0],'vertices':[]}
 return parts[name]
pivots={}
for side in [-1,1]:
 for axle,y in enumerate(centers):
  subset=wp[((wp[:,0]>0) if side>0 else (wp[:,0]<0)) & (abs(wp[:,1]-y)<.28)]
  pivot=(subset.min(axis=0)+subset.max(axis=0))/2
  pivots[(side,axle)]=pivot
faces=sum(len(o.data.polygons) for o in objects)
for o in objects:
 bounds=[o.matrix_world@Vector(p) for p in o.bound_box]
 launcher=min(v.y for v in bounds)>=1.60 and min(v.z for v in bounds)>=0 and max(v.z for v in bounds)>.15
 bpy.context.view_layer.objects.active=o
 mod=o.modifiers.new('Minecraft surface budget','DECIMATE');mod.ratio=min(1,max(6000/faces,64/max(1,len(o.data.polygons))))
 bpy.ops.object.modifier_apply(modifier=mod.name);o.data.calc_loop_triangles()
 for tri in o.data.loop_triangles:
  ps=[o.matrix_world@o.data.vertices[i].co for i in tri.vertices];middle=sum(ps,Vector())/3
  patch=math.sin(middle.x*9+math.sin(middle.y*3)*2)+math.cos(middle.y*5+middle.z*7)
  color=(.25,.29,.17) if patch>0 else (.16,.19,.10)
  if o.name=='Cube.002':
   side=1 if middle.x>0 else -1;axle=int(np.argmin(abs(centers-middle.y)));pivot=pivots[(side,axle)]
   group=part('wheel_%s_%s'%(side,axle),'wheel',pivot)
   radius=math.hypot(middle.y-pivot[1],middle.z-pivot[2]);color=(.035,.04,.037) if radius>.15 else (.26,.28,.23)
  else:
   group=part('launcher','launcher',(0,3.05,.15)) if launcher else part('chassis','', (0,0,0))
   if middle.z<low+.22:color=(.09,.105,.09)
   if o.name in ['Cube.015','Cube.032']:color=(.065,.13,.16)
  for index,li in zip(tri.vertices,tri.loops):
   p=o.matrix_world@o.data.vertices[index].co;n=o.matrix_world.to_3x3()@o.data.corner_normals[li].vector;n.normalize()
   group['vertices'].append([*[round(float(v),5) for v in convert(p)],round(n.x,4),round(n.z,4),round(-n.y,4),*color,.5,.5])
count=sum(len(p['vertices'])//3 for p in parts.values())
# Bake a single original diffuse atlas from camouflage, weathering and material masks.
allverts=[];faces_out=[];colors=[];refs=[];indices={}
for group in parts.values():
 rows=group['vertices']
 for start in range(0,len(rows),3):
  face=[]
  for row in rows[start:start+3]:
   key=tuple(row[:3])
   if key not in indices:indices[key]=len(allverts);allverts.append(key)
   face.append(indices[key]);c=row[6:9]
   colors.append((*c,1. if c[1]>c[0] and c[2]<c[1]*.75 else 0.));refs.append(row)
  faces_out.append(face)
mesh=bpy.data.meshes.new('Optimized baking surface');mesh.from_pydata(allverts,[],faces_out);mesh.update()
obj=bpy.data.objects.new('Original camouflage bake',mesh);bpy.context.collection.objects.link(obj)
bpy.ops.object.select_all(action='DESELECT');obj.select_set(True);bpy.context.view_layer.objects.active=obj
attribute=mesh.color_attributes.new(name='PaintMask',type='FLOAT_COLOR',domain='CORNER')
for i,c in enumerate(colors):attribute.data[i].color=c
bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT');bpy.ops.uv.smart_project(angle_limit=1.15,island_margin=.008);bpy.ops.object.mode_set(mode='OBJECT')
material=bpy.data.materials.new('Original FK paint and weathering');material.use_nodes=True;mesh.materials.append(material)
nodes=material.node_tree.nodes;nodes.clear();links=material.node_tree.links
output=nodes.new('ShaderNodeOutputMaterial');diffuse=nodes.new('ShaderNodeBsdfDiffuse');links.new(diffuse.outputs[0],output.inputs['Surface'])
paint=nodes.new('ShaderNodeVertexColor');paint.layer_name='PaintMask'
position=nodes.new('ShaderNodeNewGeometry');noise=nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=2.7;noise.inputs['Detail'].default_value=2.;links.new(position.outputs['Position'],noise.inputs['Vector'])
ramp=nodes.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].position=.35;ramp.color_ramp.elements[0].color=(.16,.205,.10,1);ramp.color_ramp.elements[1].position=.64;ramp.color_ramp.elements[1].color=(.30,.29,.17,1);ramp.color_ramp.elements.new(.51).color=(.08,.12,.055,1);links.new(noise.outputs['Fac'],ramp.inputs[0])
blend=nodes.new('ShaderNodeMixRGB');links.new(paint.outputs['Alpha'],blend.inputs[0]);links.new(paint.outputs['Color'],blend.inputs[1]);links.new(ramp.outputs['Color'],blend.inputs[2])
wear=nodes.new('ShaderNodeTexNoise');wear.inputs['Scale'].default_value=160.;wear.inputs['Detail'].default_value=2.;links.new(position.outputs['Position'],wear.inputs['Vector'])
finish=nodes.new('ShaderNodeMixRGB');finish.blend_type='MULTIPLY';finish.inputs[0].default_value=.17;links.new(blend.outputs[0],finish.inputs[1]);links.new(wear.outputs['Fac'],finish.inputs[2]);links.new(finish.outputs[0],diffuse.inputs['Color'])
image=bpy.data.images.new('FK2000_original_paint',width=2048,height=2048,alpha=False);target=nodes.new('ShaderNodeTexImage');target.image=image;nodes.active=target
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=4;scene.render.threads_mode='FIXED';scene.render.threads=4
# Other imported objects must not obscure the baking surface.
for other in objects:other.hide_render=True
bpy.ops.object.bake(type='DIFFUSE',pass_filter={'COLOR'},margin=4,use_clear=True)
from pathlib import Path
image.filepath_raw=str(Path(args[1]).parent.parent/'textures/entity/imported-truck.png');image.file_format='PNG';image.save()
uv=mesh.uv_layers.active
for i,row in enumerate(refs):row[6:9]=[1.,1.,1.];row[9]=round(uv.data[i].uv.x,6);row[10]=round(1.-uv.data[i].uv.y,6)
with open(args[1],'w') as f:json.dump({'format':2,'triangles':count,'parts':list(parts.values()),'source':'User supplied Truck.fbx','length':6},f,separators=(',',':'))
print('EXPORTED',count,'triangles;',len(parts),'parts; axle centers',centers.tolist(),'scale',scale,'ground',low,'center',center)
