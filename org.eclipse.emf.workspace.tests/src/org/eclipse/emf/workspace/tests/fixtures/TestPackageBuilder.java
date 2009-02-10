/**
 * <copyright>
 * 
 * Copyright (c) 2009 Christian W. Damus and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Christian W. Damus - Initial API and implementation
 * 
 * </copyright>
 *
 * $Id: TestPackageBuilder.java,v 1.1 2009/02/10 04:04:39 cdamus Exp $
 */

package org.eclipse.emf.workspace.tests.fixtures;

import java.util.Iterator;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcoreFactory;

/**
 * A builder of test packages with useful, often peculiar or uncommon, metadata.
 * The package is a dynamically created EPackage, is registered with the package
 * registry, and should be {@linkplain #dispose() disposed} at the conclusion of
 * the test.
 * 
 * @author Christian W. Damus (cdamus)
 */
@SuppressWarnings("nls")
public class TestPackageBuilder {

	protected EPackage package_;

	/**
	 * Initializes me.
	 */
	public TestPackageBuilder() {
		super();
	}

	/**
	 * Obtains my package, building it first if necessary.
	 * 
	 * @return my package
	 */
	public EPackage getPackage() {
		if (package_ == null) {
			buildPackage();
		}

		return package_;
	}
	
	public EClass getA() {
		return (EClass) getPackage().getEClassifier("A");
	}
	
	public EClass getB() {
		return (EClass) getPackage().getEClassifier("B");
	}
	
	public EReference getA_b() {
		return (EReference) getA().getEStructuralFeature("b");
	}

	/**
	 * Disposes my package.
	 */
	public void dispose() {
		if (package_ != null) {
			EPackage.Registry.INSTANCE.remove(package_.getNsURI());

			// dispose the package
			for (Iterator<EObject> iter = package_.eAllContents(); iter
				.hasNext();) {
				iter.next().eAdapters().clear();
			}
			package_.eAdapters().clear();

			package_ = null;
		}
	}

	protected void buildPackage() {
		package_ = EcoreFactory.eINSTANCE.createEPackage();
		package_.setName("emfwbtestpkg");
		package_.setNsPrefix("wbtest");
		package_.setNsURI("http://www.eclipse.org/emf/test/WorkbenchTestPackage");
		
		EClass a = eClass("A");
		EClass b = eClass("B");
		
		crossReference(a, "b", b, true, true);
		
		// register the package
		EPackage.Registry.INSTANCE.put(package_.getNsURI(), package_);
	}

	protected EClass eClass(String name) {
		EClass result = EcoreFactory.eINSTANCE.createEClass();
		result.setName(name);
		package_.getEClassifiers().add(result);
		return result;
	}

	protected EReference crossReference(EClass owner, String name, EClass type,
			boolean isMany, boolean unsettable) {

		return eReference(owner, name, type, 0,
			ETypedElement.UNBOUNDED_MULTIPLICITY, false, unsettable);
	}

	protected EReference containment(EClass owner, String name, EClass type,
			boolean isMany) {

		return eReference(owner, name, type, 0,
			ETypedElement.UNBOUNDED_MULTIPLICITY, true, false);
	}

	protected EReference eReference(EClass owner, String name, EClass type,
			int lower, int upper, boolean containment, boolean unsettable) {
		
		EReference result = EcoreFactory.eINSTANCE.createEReference();
		result.setName(name);
		owner.getEStructuralFeatures().add(result);

		result.setEType(type);
		result.setLowerBound(lower);
		result.setUpperBound(upper);
		result.setContainment(containment);
		result.setUnsettable(unsettable);

		return result;
	}
}
