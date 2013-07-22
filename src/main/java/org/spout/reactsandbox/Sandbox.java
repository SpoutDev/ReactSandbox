/*
 * This file is part of ReactSandbox.
 *
 * Copyright (c) 2013 Spout LLC <http://www.spout.org/>
 * ReactSandbox is licensed under the Spout License Version 1.
 *
 * ReactSandbox is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * In addition, 180 days after any changes are published, you can use the
 * software, incorporating those changes, under the terms of the MIT license,
 * as described in the Spout License Version 1.
 *
 * ReactSandbox is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License,
 * the MIT license and the Spout License Version 1 along with this program.
 * If not, see <http://www.gnu.org/licenses/> for the GNU Lesser General Public
 * License and see <http://spout.in/licensev1> for the full license, including
 * the MIT license.
 */
package org.spout.reactsandbox;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.yaml.snakeyaml.Yaml;

import org.spout.physics.body.CollisionBody;
import org.spout.physics.body.ImmobileRigidBody;
import org.spout.physics.body.MobileRigidBody;
import org.spout.physics.body.RigidBody;
import org.spout.physics.body.RigidBodyMaterial;
import org.spout.physics.collision.RayCaster.IntersectedBody;
import org.spout.physics.collision.shape.AABB;
import org.spout.physics.collision.shape.BoxShape;
import org.spout.physics.collision.shape.CollisionShape;
import org.spout.physics.collision.shape.ConeShape;
import org.spout.physics.collision.shape.CylinderShape;
import org.spout.physics.collision.shape.SphereShape;
import org.spout.physics.engine.DynamicsWorld;
import org.spout.physics.math.Quaternion;
import org.spout.physics.math.Transform;
import org.spout.physics.math.Vector3;
import org.spout.renderer.Camera;
import org.spout.renderer.GLVersion;
import org.spout.renderer.Material;
import org.spout.renderer.Model;
import org.spout.renderer.Model.DrawMode;
import org.spout.renderer.Program;
import org.spout.renderer.Renderer;
import org.spout.renderer.Shader.ShaderType;
import org.spout.renderer.data.Uniform.BooleanUniform;
import org.spout.renderer.data.Uniform.ColorUniform;
import org.spout.renderer.data.Uniform.FloatUniform;
import org.spout.renderer.data.Uniform.Vector3Uniform;
import org.spout.renderer.data.UniformHolder;

/**
 * The main class of the ReactSandbox.
 */
public class Sandbox {
	// Constants
	private static final String WINDOW_TITLE = "React Sandbox";
	private static final float TIMESTEP = 1f / 60;
	private static final int TIMESTEP_MILLISEC = Math.round(TIMESTEP * 1000);
	private static final RigidBodyMaterial WOOD_MATERIAL = RigidBodyMaterial.asUnmodifiableMaterial(new RigidBodyMaterial(0.6f, 0.4f));
	// Settings
	private static float mouseSensitivity = 0.08f;
	private static float cameraSpeed = 0.2f;
	private static int windowWidth = 1200;
	private static int windowHeight = 800;
	private static float fieldOfView = 75;
	private static Color aabbColor;
	private static Color boxShapeColor;
	private static Color coneShapeColor;
	private static Color cylinderShapeColor;
	private static Color sphereShapeColor;
	// Physics objects
	private static DynamicsWorld world;
	private static Vector3 gravity = new Vector3(0, -9.81f, 0);
	private static final Map<CollisionBody, Model> shapes = new HashMap<CollisionBody, Model>();
	private static final Map<CollisionBody, Model> aabbs = new HashMap<CollisionBody, Model>();
	// Input
	private static boolean mouseGrabbed = true;
	private static float cameraPitch = 0;
	private static float cameraYaw = 0;
	// Selection
	private static CollisionBody selected = null;
	private static float targetSize = 0.1f;
	private static Color targetColor = new Color(1f, 1f, 0f, 1f);
	// Rendering
	private static GLVersion glVersion;
	private static Renderer renderer;
	private static Color backgroundColor;
	// Rendering material for objects
	private static Material solidMaterial;
	private static Material wireframeMaterial;
	// Lighting
	private static org.spout.math.vector.Vector3 lightPosition = new org.spout.math.vector.Vector3(0, 0, 0);
	private static float diffuseIntensity = 0.8f;
	private static float specularIntensity = 0.2f;
	private static float ambientIntensity = 0.3f;
	private static float lightAttenuation = 0.03f;

	/**
	 * Entry point for the application.
	 *
	 * @param args Unused
	 */
	public static void main(String[] args) {
		try {
			deploy();
			loadConfiguration();
			setupRenderer();
			System.out.println("Starting up");
			System.out.println("OpenGL version: " + glVersion);
			world = new DynamicsWorld(gravity, TIMESTEP);
			addDefaultBodies();
			Mouse.setGrabbed(true);
			world.start();
			renderer.getCamera().setPosition(SandboxUtil.toMathVector3(new Vector3(0, 5, 10)));
			while (!Display.isCloseRequested()) {
				final long start = System.nanoTime();
				processInput();
				world.update();
				handleSelection();
				updateBodies();
				renderer.render();
				final long delta = Math.round((System.nanoTime() - start) / 1000000d);
				Thread.sleep(Math.max(TIMESTEP_MILLISEC - delta, 0));
			}
			System.out.println("Shutting down");
			world.stop();
			Mouse.setGrabbed(false);
			renderer.destroy();
		} catch (Exception ex) {
			ex.printStackTrace();
			final String name = ex.getClass().getSimpleName();
			final String message = ex.getMessage();
			Sys.alert("Error: " + name, message == null || message.trim().equals("") ? name : message);
			System.exit(-1);
		}
	}

	private static ImmobileRigidBody addImmobileBody(CollisionShape shape, float mass, Vector3 position, Quaternion orientation) {
		final ImmobileRigidBody body = world.createImmobileRigidBody(new Transform(position, orientation), mass, shape);
		body.setMaterial(WOOD_MATERIAL);
		addBody(body);
		return body;
	}

	private static MobileRigidBody addMobileBody(CollisionShape shape, float mass, Vector3 position, Quaternion orientation) {
		final MobileRigidBody body = world.createMobileRigidBody(new Transform(position, orientation), mass, shape);
		body.setMaterial(WOOD_MATERIAL);
		addBody(body);
		return body;
	}

	private static CollisionBody addBody(CollisionBody body) {
		final Transform bodyTransform = body.getTransform();
		final Vector3 bodyPosition = bodyTransform.getPosition();
		final Quaternion bodyOrientation = bodyTransform.getOrientation();
		final AABB aabb = body.getAABB();
		final Model aabbModel = glVersion.createModel();
		MeshGenerator.generateWireCuboid(aabbModel, new Vector3(1, 1, 1));
		aabbModel.setMaterial(wireframeMaterial);
		aabbModel.setDrawMode(DrawMode.LINES);
		aabbModel.setScale(SandboxUtil.toMathVector3(Vector3.subtract(aabb.getMax(), aabb.getMin())));
		aabbModel.setPosition(SandboxUtil.toMathVector3(bodyPosition));
		aabbModel.getUniforms().add(new ColorUniform("modelColor", aabbColor));
		aabbModel.create();
		renderer.addModel(aabbModel);
		aabbs.put(body, aabbModel);
		final CollisionShape shape = body.getCollisionShape();
		final Model shapeModel = glVersion.createModel();
		switch (shape.getType()) {
			case BOX:
				final BoxShape box = (BoxShape) shape;
				MeshGenerator.generateCuboid(shapeModel, Vector3.multiply(box.getExtent(), 2));
				shapeModel.getUniforms().add(new ColorUniform("modelColor", boxShapeColor));
				break;
			case CONE:
				final ConeShape cone = (ConeShape) shape;
				MeshGenerator.generateCone(shapeModel, cone.getRadius(), cone.getHeight());
				shapeModel.getUniforms().add(new ColorUniform("modelColor", coneShapeColor));
				break;
			case CYLINDER:
				final CylinderShape cylinder = (CylinderShape) shape;
				MeshGenerator.generateCylinder(shapeModel, cylinder.getRadius(), cylinder.getHeight());
				shapeModel.getUniforms().add(new ColorUniform("modelColor", cylinderShapeColor));
				break;
			case SPHERE:
				final SphereShape sphere = (SphereShape) shape;
				MeshGenerator.generateSphere(shapeModel, sphere.getRadius());
				shapeModel.getUniforms().add(new ColorUniform("modelColor", sphereShapeColor));
				break;
			default:
				throw new IllegalArgumentException("Unsupported collision shape: " + shape.getType());
		}
		shapeModel.setMaterial(solidMaterial);
		shapeModel.setPosition(SandboxUtil.toMathVector3(bodyPosition));
		shapeModel.setRotation(SandboxUtil.toMathQuaternion(bodyOrientation));
		shapeModel.create();
		renderer.addModel(shapeModel);
		shapes.put(body, shapeModel);
		return body;
	}

	private static void removeBody(final CollisionBody body) {
		final Model shapeModel = shapes.remove(body);
		renderer.removeModel(shapeModel);
		shapeModel.destroy();
		final Model aabbModel = aabbs.remove(body);
		renderer.removeModel(aabbModel);
		aabbModel.destroy();
		if (body instanceof RigidBody) {
			world.destroyRigidBody((RigidBody) body);
		}
	}

	private static void updateBodies() {
		for (Entry<CollisionBody, Model> entry : shapes.entrySet()) {
			final CollisionBody body = entry.getKey();
			final Model shape = entry.getValue();
			final Model aabbModel = aabbs.get(body);
			final AABB aabb = body.getAABB();
			final Transform transform = body.getTransform();
			final Vector3 position = transform.getPosition();
			aabbModel.setPosition(SandboxUtil.toMathVector3(position));
			aabbModel.setScale(SandboxUtil.toMathVector3(Vector3.subtract(aabb.getMax(), aabb.getMin())));
			shape.setPosition(SandboxUtil.toMathVector3(position));
			shape.setRotation(SandboxUtil.toMathQuaternion(transform.getOrientation()));
		}
	}

	private static void processInput() {
		final boolean mouseGrabbedBefore = mouseGrabbed;
		while (Keyboard.next()) {
			if (Keyboard.getEventKeyState()) {
				if (Keyboard.getEventKey() == Keyboard.KEY_ESCAPE) {
					mouseGrabbed ^= true;
				}
				if (Keyboard.getEventKey() == Keyboard.KEY_X) {
					if (selected != null) {
						removeBody(selected);
					}
				}
			}
		}
		final Camera camera = renderer.getCamera();
		if (Display.isActive()) {
			if (mouseGrabbed != mouseGrabbedBefore) {
				Mouse.setGrabbed(mouseGrabbed);
			}
			if (mouseGrabbed) {
				cameraPitch -= Mouse.getDX() * mouseSensitivity;
				cameraPitch %= 360;
				final Quaternion pitch = SandboxUtil.angleAxisToQuaternion(cameraPitch, 0, 1, 0);
				cameraYaw += Mouse.getDY() * mouseSensitivity;
				cameraYaw %= 360;
				final Quaternion yaw = SandboxUtil.angleAxisToQuaternion(cameraYaw, 1, 0, 0);
				camera.setRotation(SandboxUtil.toMathQuaternion(Quaternion.multiply(pitch, yaw)));
			}
		}
		final Vector3 right = SandboxUtil.toReactVector3(camera.getRight());
		final Vector3 up = SandboxUtil.toReactVector3(camera.getUp());
		final Vector3 forward = SandboxUtil.toReactVector3(camera.getForward());
		final Vector3 position = SandboxUtil.toReactVector3(camera.getPosition());
		if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
			position.add(Vector3.multiply(forward, cameraSpeed));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_S)) {
			position.add(Vector3.multiply(forward, -cameraSpeed));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
			position.add(Vector3.multiply(right, cameraSpeed));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_D)) {
			position.add(Vector3.multiply(right, -cameraSpeed));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
			position.add(Vector3.multiply(up, cameraSpeed));
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
			position.add(Vector3.multiply(up, -cameraSpeed));
		}
		camera.setPosition(SandboxUtil.toMathVector3(position));
		solidMaterial.getUniforms().getVector3("lightPosition").set(SandboxUtil.toMathVector3(position));
	}

	private static void handleSelection() {
		if (selected != null) {
			aabbs.get(selected).getUniforms().getColor("modelColor").set(aabbColor);
			selected = null;
		}
		solidMaterial.getUniforms().getBoolean("displayTarget").set(false);
		final IntersectedBody targeted = world.findClosestIntersectingBody(
				SandboxUtil.toReactVector3(renderer.getCamera().getPosition()),
				SandboxUtil.toReactVector3(renderer.getCamera().getForward()));
		if (targeted != null && targeted.getBody() instanceof RigidBody) {
			selected = targeted.getBody();
			aabbs.get(selected).getUniforms().getColor("modelColor").set(Color.BLUE);
			solidMaterial.getUniforms().getVector3("targetPosition").set(SandboxUtil.toMathVector3(targeted.getIntersectionPoint()));
			solidMaterial.getUniforms().getBoolean("displayTarget").set(true);
		}
	}

	private static void addDefaultBodies() {
		addMobileBody(new BoxShape(new Vector3(1, 1, 1)), 1, new Vector3(0, 6, 0), SandboxUtil.angleAxisToQuaternion(45, 1, 1, 1));
		addMobileBody(new BoxShape(new Vector3(0.28f, 0.28f, 0.28f)), 1, new Vector3(0, 6, 0), SandboxUtil.angleAxisToQuaternion(45, 1, 1, 1));
		addMobileBody(new ConeShape(1, 2), 1, new Vector3(0, 9, 0), SandboxUtil.angleAxisToQuaternion(89, -1, -1, -1));
		addMobileBody(new CylinderShape(1, 2), 1, new Vector3(0, 12, 0), SandboxUtil.angleAxisToQuaternion(-15, 1, -1, 1));
		addMobileBody(new SphereShape(1), 1, new Vector3(0, 15, 0), SandboxUtil.angleAxisToQuaternion(32, -1, -1, 1));
		addImmobileBody(new BoxShape(new Vector3(10, 1, 10)), 20, new Vector3(0, 1.8f, 0), Quaternion.identity());
		addImmobileBody(new BoxShape(new Vector3(50, 1, 50)), 100, new Vector3(0, 0, 0), Quaternion.identity());
	}

	private static void setupRenderer() {
		renderer = glVersion.createRenderer();
		renderer.setBackgroundColor(backgroundColor);
		renderer.setWindowTitle(WINDOW_TITLE);
		renderer.setWindowSize(windowWidth, windowHeight);
		renderer.setCamera(Camera.createPerspective(fieldOfView, windowWidth, windowHeight, 0.001f, 1000));
		renderer.create();
		final String path = "/" + glVersion.toString().toLowerCase() + "/";
		solidMaterial = glVersion.createMaterial();
		final Program solidProgram = solidMaterial.getProgram();
		solidProgram.addShaderSource(ShaderType.VERTEX, Sandbox.class.getResourceAsStream(path + "solid.vert"));
		solidProgram.addShaderSource(ShaderType.FRAGMENT, Sandbox.class.getResourceAsStream(path + "solid.frag"));
		solidProgram.addAttributeLayout("position", 0);
		solidProgram.addAttributeLayout("normal", 1);
		final UniformHolder solidUniforms = solidMaterial.getUniforms();
		solidUniforms.add(new Vector3Uniform("lightPosition", lightPosition));
		solidUniforms.add(new FloatUniform("diffuseIntensity", diffuseIntensity));
		solidUniforms.add(new FloatUniform("specularIntensity", specularIntensity));
		solidUniforms.add(new FloatUniform("ambientIntensity", ambientIntensity));
		solidUniforms.add(new FloatUniform("lightAttenuation", lightAttenuation));
		solidUniforms.add(new Vector3Uniform("targetPosition", org.spout.math.vector.Vector3.ZERO));
		solidUniforms.add(new BooleanUniform("displayTarget", false));
		solidUniforms.add(new FloatUniform("targetSize", targetSize));
		solidUniforms.add(new ColorUniform("targetColor", targetColor));
		solidMaterial.create();
		wireframeMaterial = glVersion.createMaterial();
		final Program wireframeProgram = wireframeMaterial.getProgram();
		wireframeProgram.addShaderSource(ShaderType.VERTEX, Sandbox.class.getResourceAsStream(path + "wireframe.vert"));
		wireframeProgram.addShaderSource(ShaderType.FRAGMENT, Sandbox.class.getResourceAsStream(path + "wireframe.frag"));
		wireframeMaterial.create();
	}

	@SuppressWarnings("unchecked")
	private static void loadConfiguration() throws Exception {
		try {
			final Map<String, Object> config =
					(Map<String, Object>) new Yaml().load(new FileInputStream("config.yml"));
			final Map<String, Object> inputConfig = (Map<String, Object>) config.get("Input");
			final Map<String, Object> appearanceConfig = (Map<String, Object>) config.get("Appearance");
			mouseSensitivity = ((Number) inputConfig.get("MouseSensitivity")).floatValue();
			cameraSpeed = ((Number) inputConfig.get("CameraSpeed")).floatValue();
			glVersion = GLVersion.valueOf(((String) appearanceConfig.get("GLVersion")).toUpperCase());
			final String[] windowSize = ((String) appearanceConfig.get("WindowSize")).split(",");
			windowWidth = Integer.parseInt(windowSize[0].trim());
			windowHeight = Integer.parseInt(windowSize[1].trim());
			fieldOfView = ((Number) appearanceConfig.get("FieldOfView")).floatValue();
			backgroundColor = parseColor(((String) appearanceConfig.get("BackgroundColor")), 0);
			aabbColor = parseColor(((String) appearanceConfig.get("AABBColor")), 1);
			boxShapeColor = parseColor(((String) appearanceConfig.get("BoxShapeColor")), 1);
			coneShapeColor = parseColor(((String) appearanceConfig.get("ConeShapeColor")), 1);
			sphereShapeColor = parseColor(((String) appearanceConfig.get("SphereShapeColor")), 1);
			cylinderShapeColor = parseColor(((String) appearanceConfig.get("CylinderShapeColor")), 1);
			diffuseIntensity = ((Number) appearanceConfig.get("DiffuseIntensity")).floatValue();
			specularIntensity = ((Number) appearanceConfig.get("SpecularIntensity")).floatValue();
			ambientIntensity = ((Number) appearanceConfig.get("AmbientIntensity")).floatValue();
			lightAttenuation = ((Number) appearanceConfig.get("LightAttenuation")).floatValue();
			targetColor = parseColor(((String) appearanceConfig.get("TargetColor")), 1);
			targetSize = ((Number) appearanceConfig.get("TargetSize")).floatValue();
		} catch (Exception ex) {
			throw new IllegalStateException("Malformed config.yml: \"" + ex.getMessage() + "\".", ex);
		}
	}

	private static void deploy() throws Exception {
		final File configFile = new File("config.yml");
		if (!configFile.exists()) {
			FileUtils.copyInputStreamToFile(Sandbox.class.getResourceAsStream("/config.yml"), configFile);
		}
		final String osPath;
		final String[] nativeLibs;
		if (SystemUtils.IS_OS_WINDOWS) {
			nativeLibs = new String[]{
					"jinput-dx8_64.dll", "jinput-dx8.dll", "jinput-raw_64.dll", "jinput-raw.dll",
					"jinput-wintab.dll", "lwjgl.dll", "lwjgl64.dll", "OpenAL32.dll", "OpenAL64.dll"
			};
			osPath = "windows/";
		} else if (SystemUtils.IS_OS_MAC) {
			nativeLibs = new String[]{
					"libjinput-osx.jnilib", "liblwjgl.jnilib", "openal.dylib"
			};
			osPath = "mac/";
		} else if (SystemUtils.IS_OS_LINUX) {
			nativeLibs = new String[]{
					"liblwjgl.so", "liblwjgl64.so", "libopenal.so", "libopenal64.so", "libjinput-linux.so",
					"libjinput-linux64.so"
			};
			osPath = "linux/";
		} else {
			throw new IllegalStateException("Could not get lwjgl natives for OS \"" + SystemUtils.OS_NAME + "\".");
		}
		final File nativesDir = new File("natives" + File.separator + osPath);
		nativesDir.mkdirs();
		for (String nativeLib : nativeLibs) {
			final File nativeFile = new File(nativesDir, nativeLib);
			if (!nativeFile.exists()) {
				FileUtils.copyInputStreamToFile(Sandbox.class.getResourceAsStream("/" + nativeLib), nativeFile);
			}
		}
		final String nativesPath = nativesDir.getAbsolutePath();
		System.setProperty("org.lwjgl.librarypath", nativesPath);
		System.setProperty("net.java.games.input.librarypath", nativesPath);
	}

	private static Color parseColor(String s, float alpha) {
		final String[] ss = s.split(",");
		return new Color(
				Float.parseFloat(ss[0].trim()),
				Float.parseFloat(ss[1].trim()),
				Float.parseFloat(ss[2].trim()),
				alpha);
	}
}
