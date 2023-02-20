/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.tests.constraints;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.validation.AbstractModelConstraint;
import org.eclipse.emf.validation.EMFEventType;
import org.eclipse.emf.validation.IValidationContext;

/**
 * Constraint used for testing transaction validation scenarios.
 * Requires books to have non-<code>null</code>, non-empty titles.
 *
 * @author Christian W. Damus (cdamus)
 */
public class BookTitleConstraint
	extends AbstractModelConstraint {

	@Override
	public IStatus validate(IValidationContext ctx) {
		EMFEventType eType = ctx.getEventType();
		
		if (eType != EMFEventType.NULL) {
			Object newValue = ctx.getFeatureNewValue();
			
			if (newValue == null
				|| ((String)newValue).length() == 0) {
				return ctx.createFailureStatus();
			}
		}
		
		return ctx.createSuccessStatus();
	}
}
