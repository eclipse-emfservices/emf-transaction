/**
 * Copyright (c) 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.tests.fixtures;

import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.ReadOnlyValidatorImpl;
import org.eclipse.emf.transaction.impl.ReadWriteValidatorImpl;
import org.eclipse.emf.transaction.impl.TransactionValidator;
import org.eclipse.emf.transaction.impl.TransactionalEditingDomainImpl;
import org.eclipse.emf.validation.model.EvaluationMode;
import org.eclipse.emf.validation.service.IConstraintDescriptor;
import org.eclipse.emf.validation.service.IConstraintFilter;
import org.eclipse.emf.validation.service.ILiveValidator;
import org.eclipse.emf.validation.service.IValidator;
import org.eclipse.emf.validation.service.ModelValidationService;

/**
 * Editing domain implementation used to test the validator creation during
 * transaction commit.
 *
 * @author David Cummings (dcummin)
 */
public class TestValidationEditingDomain extends TransactionalEditingDomainImpl {
	
	public TestValidationEditingDomain(AdapterFactory adapterFactory) {
		super(adapterFactory);
	}

	public static int readWriteValidatorHitCount = 0;
	
	public static boolean enableCustomValidator = false;
					
	public static class FactoryImpl extends TransactionalEditingDomainImpl.FactoryImpl {

		@Override
		public TransactionalEditingDomain createEditingDomain() {
			TransactionalEditingDomainImpl result = new TestValidationEditingDomain(
					new ComposedAdapterFactory(
						ComposedAdapterFactory.Descriptor.Registry.INSTANCE));
			
			result.setValidatorFactory(new TestValidatorFactory());
			
			mapResourceSet(result);

			return result;
		}

		@Override
		public TransactionalEditingDomain createEditingDomain(ResourceSet rset) {
			// not used by the extension point
			return null;
		}

		@Override
		public TransactionalEditingDomain getEditingDomain(ResourceSet rset) {
			// not used by the extension point
			return null;
		}
		
		public class TestValidatorFactory implements TransactionValidator.Factory {
			public TransactionValidator createReadOnlyValidator() {
				return new ReadOnlyValidatorImpl();
			}

			public TransactionValidator createReadWriteValidator() {
				return new TestReadWriteValidatorImpl();
			}
		}
		
		public class TestReadWriteValidatorImpl extends ReadWriteValidatorImpl {
			@Override
			protected IValidator<Notification> createValidator() {
				if (enableCustomValidator) {
					readWriteValidatorHitCount++;
					ILiveValidator validator = ModelValidationService.getInstance().newValidator(
						EvaluationMode.LIVE);
					validator.addConstraintFilter(new IConstraintFilter() {
						public boolean accept(IConstraintDescriptor constraint,
								EObject target) {
							return false;
						}});
					return validator;
				}
				return super.createValidator();
			}
		}
	}
}
