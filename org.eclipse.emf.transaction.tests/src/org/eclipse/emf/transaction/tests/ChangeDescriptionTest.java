/*******************************************************************************
 * Copyright (c) 2015 Obeo, Christian W. Damus, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Obeo - initial API and implementation
 *    Christian W. Damus - bug 460206
 *******************************************************************************/
package org.eclipse.emf.transaction.tests;

import java.io.File;
import java.util.Collections;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.ChangeFactory;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.edit.EMFEditPlugin;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListenerImpl;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.TransactionalEditingDomain;

/**
 * A test case for https://bugs.eclipse.org/bugs/show_bug.cgi?id=460206
 * 
 * @author <a href="mailto:esteban.dugueperoux@obeo.fr">Esteban Dugueperoux</a>
 */
public class ChangeDescriptionTest extends TestCase {

	private File tempFile;

	private ResourceSet resourceSet;

	private Resource resource;

	private EPackage rootEPackage1;

	public static Test suite() {
		return new TestSuite(ChangeDescriptionTest.class, "Change Description Tests"); //$NON-NLS-1$
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		resourceSet = new ResourceSetImpl();
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
		tempFile = File.createTempFile("test", ".ecore");
		URI uri = URI.createFileURI(tempFile.getCanonicalPath());
		resource = resourceSet.createResource(uri);
		rootEPackage1 = EcoreFactory.eINSTANCE.createEPackage();
		resource.getContents().add(rootEPackage1);
	}

	public void testChangeDescriptionInNonEMFTEditingDomain1() {
		testChangeDescriptionInNonEMFTEditingDomain(rootEPackage1);
	}

	public void testChangeDescriptionInNonEMFTEditingDomain2() {
		EPackage rootEPackage2 = EcoreFactory.eINSTANCE.createEPackage();
		resource.getContents().add(rootEPackage2);
		testChangeDescriptionInNonEMFTEditingDomain(rootEPackage2);
	}

	private void testChangeDescriptionInNonEMFTEditingDomain(EPackage targetOfSecondEClass) {
		ComposedAdapterFactory.Descriptor.Registry registry = EMFEditPlugin.getComposedAdapterFactoryDescriptorRegistry();
		AdapterFactory adapterFactory = new ComposedAdapterFactory(registry);
		BasicCommandStack commandStack = new BasicCommandStack();
		EditingDomain domain = new AdapterFactoryEditingDomain(adapterFactory, commandStack, resourceSet);

		ChangeRecorder changeRecorder = new ChangeRecorder(resourceSet);

		ChangeDescription changeDescription = ChangeFactory.eINSTANCE.createChangeDescription();
		changeRecorder.beginRecording(changeDescription, Collections.singleton(resourceSet));
		EClass eClass = EcoreFactory.eINSTANCE.createEClass();
		Command addCmd = AddCommand.create(domain, rootEPackage1, EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, eClass);
		addCmd = addCmd.chain(AddCommand.create(domain, targetOfSecondEClass, EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, EcoreFactory.eINSTANCE.createEClass()));
		domain.getCommandStack().execute(addCmd);

		changeRecorder.endRecording();

		assertEquals(targetOfSecondEClass != rootEPackage1 ? 2 : 1, changeDescription.getObjectChanges().size());
		assertEquals(2, changeDescription.getObjectsToDetach().size());

		changeRecorder.dispose();
	}

	public void testChangeDescriptionInEMFTEditingDomain1() {
		testChangeDescriptionInEMFTEditingDomain(rootEPackage1);
	}

	public void testChangeDescriptionInEMFTEditingDomain2() {
		EPackage rootEPackage2 = EcoreFactory.eINSTANCE.createEPackage();
		resource.getContents().add(rootEPackage2);
		testChangeDescriptionInEMFTEditingDomain(rootEPackage2);
	}

	private void testChangeDescriptionInEMFTEditingDomain(EPackage targetOfPrecommit) {
		TransactionalEditingDomain domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain(resourceSet);

		ChangeRecorder changeRecorder = new ChangeRecorder(resourceSet);

		ChangeDescription changeDescription = ChangeFactory.eINSTANCE.createChangeDescription();
		changeRecorder.beginRecording(changeDescription, Collections.singleton(resourceSet));

		EClassAdder eClassAdder = new EClassAdder(targetOfPrecommit);
		domain.addResourceSetListener(eClassAdder);

		EClass eClass = EcoreFactory.eINSTANCE.createEClass();
		eClass.setName("EClassFromCommand");
		Command addCmd = AddCommand.create(domain, rootEPackage1, EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, eClass);
		domain.getCommandStack().execute(addCmd);

		changeRecorder.endRecording();

		assertEquals(targetOfPrecommit != rootEPackage1 ? 2 : 1, changeDescription.getObjectChanges().size());
		assertEquals(2, changeDescription.getObjectsToDetach().size());

		changeRecorder.dispose();
	}

	private static class EClassAdder extends ResourceSetListenerImpl {

		private EPackage rootEPackage;

		private boolean added;

		public EClassAdder(EPackage rootEPackage) {
			this.rootEPackage = rootEPackage;
		}

		@Override
		public boolean isAggregatePrecommitListener() {
			return true;
		}

		@Override
		public Command transactionAboutToCommit(ResourceSetChangeEvent event) throws RollbackException {
			if(!added) {
				added = true;
				EClass eClass = EcoreFactory.eINSTANCE.createEClass();
				eClass.setName("EClassFromPrecommit");
				return AddCommand.create(getTarget(), rootEPackage, EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, eClass);
			}
			return null;

		}

		@Override
		public void resourceSetChanged(ResourceSetChangeEvent event) {
			super.resourceSetChanged(event);
			ChangeDescription changeDescription = event.getTransaction().getChangeDescription();
			EList<EObject> objectsToDetach = changeDescription.getObjectsToDetach();
			assertEquals(2, objectsToDetach.size());
			changeDescription = null;
		}

	}

	@Override
	protected void tearDown() throws Exception {
		rootEPackage1 = null;
		resource = null;
		resourceSet = null;
		tempFile.delete();
		tempFile = null;
		super.tearDown();
	}

}
