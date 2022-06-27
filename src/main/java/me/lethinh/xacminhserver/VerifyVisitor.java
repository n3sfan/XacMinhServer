package me.lethinh.xacminhserver;

import org.objectweb.asm.ClassVisitor;

public abstract class VerifyVisitor extends ClassVisitor {

    public String mcVersion;
    public boolean failed;

    public VerifyVisitor(int api, ClassVisitor visitor) {
        super(api, visitor);
        failed = false;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        changeClassName(name);
        super.visit(version, access, mcVersion, signature, superName, interfaces);
    }

    // mc version = class name
    public abstract void changeClassName(String className);

}
