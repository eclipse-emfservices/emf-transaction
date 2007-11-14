/**
 * <copyright>
 *
 * Copyright (c) 2006, 2007 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: PerformanceTest.java,v 1.5 2007/11/14 18:14:12 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.BookCategory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.ResourceSetListenerImpl;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.tests.fixtures.TestCommand;


/**
 * Regression tests for performance problems (time and memory).
 *
 * @author Christian W. Damus (cdamus)
 */
public class PerformanceTest extends AbstractTest {
	static final int RUNS = 10;
	static final int OUTLIERS = 2;  // room for 1 high and 1 low outlier
	
	private List<Long> timings;
	private long clock;
	
	private final int count = RUNS + OUTLIERS;
	
	public PerformanceTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(PerformanceTest.class, "Performance Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests the time required to create a deeply nested transaction tree and
	 * commit it, for regression in the performance of the
	 * <code>ReadWriteValidator.findTree(Transaction)</code> method.
	 */
	public void test_ReadWriteValidator_findTree_132590() {
		for (int i = 0; i < count; i++) {
			startClock();
			
			createDeeplyNestedTransaction(5, 10);
			
			long timing = stopClock();
			
			System.out.println("Raw timing: " + timing); //$NON-NLS-1$
		}
	}
	
	/**
	 * Measures the time taken to load a largish resource, to measure the
	 * performance benefit of not recording resource load/unload events.
	 */
	public void test_loadLargishModelWithoutRecording() {
		addStuffToTestResourceAndClose();

		final Transaction[] originalTransaction = new Transaction[1];
		final int[] precommitEvents = new int[1];
		final int[] postcommitEvents = new int[1];
		
		// add a listener to incur the cost of broadcasting pre- and post-commit
		ResourceSetListener listener = new ResourceSetListenerImpl() {
			@Override
			public Command transactionAboutToCommit(ResourceSetChangeEvent event)
					throws RollbackException {
				CompoundCommand result = null;
				
				if (originalTransaction[0] == null) {
					originalTransaction[0] = event.getTransaction();
				}
				
				if (event.getTransaction() == originalTransaction[0]) {
					// only create a command on the first transaction
					result = new CompoundCommand();
					
					for (Iterator<?> iter = event.getNotifications().iterator(); iter.hasNext();) {
						iter.next();
						
						precommitEvents[0]++;
						
						result.append(new TestCommand() {
							public void execute() {/* nothing to do */}});
					}
				}
				
				return result;
			}
		
			@Override
			public void resourceSetChanged(ResourceSetChangeEvent event) {
				for (Iterator<?> iter = event.getNotifications().iterator(); iter.hasNext();) { 
					iter.next();
					
					// do a trivial amount of work
					postcommitEvents[0]++;
					assertNotNull(event.getTransaction());
				}
			}};

		domain.addResourceSetListener(listener);
		
		Map<Object, Object> options = new java.util.HashMap<Object, Object>();
//		options.put(Transaction.OPTION_NO_NOTIFICATIONS, Boolean.TRUE);
//		options.put(Transaction.OPTION_NO_TRIGGERS, Boolean.TRUE);
//		options.put(Transaction.OPTION_NO_VALIDATION, Boolean.TRUE);
		
		try {
			for (int i = 0; i < count; i++) {
				originalTransaction[0] = null;
				
				// we will report the number of events from just a single run
				precommitEvents[0] = 0;
				postcommitEvents[0] = 0;
				
				startClock();
				
				startWriting(options);
				
				testResource.load(Collections.EMPTY_MAP);
				
				commit();
				
				startWriting(options);
				
				testResource.unload();
				
				commit();
				
				long timing = stopClock();
				
				System.out.println("Raw timing: " + timing); //$NON-NLS-1$
			}
			
			System.out.println("Number of pre-commit events : " + precommitEvents[0]); //$NON-NLS-1$
			System.out.println("Number of post-commit events: " + postcommitEvents[0]); //$NON-NLS-1$
		} catch (IOException e) {
			e.printStackTrace();
			fail("Failed to load test resource: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} finally {
			domain.removeResourceSetListener(listener);
		}
	}
	
	/**
	 * Measures the performance of a simple deeply-nested transaction structure
	 * with options differing by depth, to gauge the performance benefit of
	 * optimizations done for bug 152332 in the ReadWriteValidatorImpl.
	 */
	public void test_validatorTransactionTreeOptimizations_152332() {
		final Transaction[] originalTransaction = new Transaction[1];
		final int[] totalNotifications = new int[1];
		final int[] expectedPrecommitNotifications = new int[1];
		final int[] expectedPostcommitNotifications = new int[1];
		final int[] expectedValidationNotifications = new int[1];
		final int[] receivedPrecommitNotifications = new int[1];
		final int[] receivedPostcommitNotifications = new int[1];
		final int[] receivedValidationNotifications = new int[1];
		
		ResourceSetListener prePostCommitCollector = new ResourceSetListenerImpl() {
			@Override
			public NotificationFilter getFilter() {
				return NotificationFilter.ANY;
			}
			
			@Override
			public Command transactionAboutToCommit(ResourceSetChangeEvent event) throws RollbackException {
				receivedPrecommitNotifications[0] += event.getNotifications().size();
				return null;
			}
			
			@Override
			public void resourceSetChanged(ResourceSetChangeEvent event) {
				receivedPostcommitNotifications[0] = event.getNotifications().size();
			}};
		domain.addResourceSetListener(prePostCommitCollector);
		ResourceSetListener validationCollector = new ResourceSetListenerImpl() {
			@Override
			public NotificationFilter getFilter() {
				return NotificationFilter.ANY;
			}
			
			@Override
			public boolean isPrecommitOnly() {
				return true;
			}
			@Override
			public boolean isAggregatePrecommitListener() {
				return true;
			}
			
			@Override
			public Command transactionAboutToCommit(ResourceSetChangeEvent event) throws RollbackException {
				receivedValidationNotifications[0] =
					((InternalTransactionalEditingDomain) domain).getValidator()
						.getNotificationsForValidation(event.getTransaction()).size();
				return null;
			}};
		domain.addResourceSetListener(validationCollector);
		
		try {
			for (int i = 0; i < count; i++) {
				originalTransaction[0] = null;
				
				// we will report the number of events from just a single run
				totalNotifications[0] = 0;
				expectedPrecommitNotifications[0] = 0;
				expectedPostcommitNotifications[0] = 0;
				expectedValidationNotifications[0] = 0;
				receivedPrecommitNotifications[0] = 0;
				receivedPostcommitNotifications[0] = 0;
				receivedValidationNotifications[0] = 0;
				
				startClock();
				
				createTreeOfMixedTransactions(5, 10,
						totalNotifications,
						expectedPrecommitNotifications,
						expectedPostcommitNotifications,
						expectedValidationNotifications);
				
				long timing = stopClock();
				
				System.out.println("Raw timing: " + timing); //$NON-NLS-1$
			}

			System.out.println("Total number of notifications: " + totalNotifications[0]); //$NON-NLS-1$
			System.out.println("Number of pre-commit notifications sent: " + receivedPrecommitNotifications[0]); //$NON-NLS-1$
			System.out.println("Number of post-commit notifications sent: " + receivedPostcommitNotifications[0]); //$NON-NLS-1$
			System.out.println("Number of validation notifications sent: " + receivedValidationNotifications[0]); //$NON-NLS-1$
			
			assertEquals("Wrong number of pre-commit notifications", //$NON-NLS-1$
					expectedPrecommitNotifications[0], receivedPrecommitNotifications[0]);
			assertEquals("Wrong number of post-commit notifications", //$NON-NLS-1$
					expectedPostcommitNotifications[0], receivedPostcommitNotifications[0]);
			assertEquals("Wrong number of validation notifications", //$NON-NLS-1$
					expectedValidationNotifications[0], receivedValidationNotifications[0]);
		} finally {
			domain.removeResourceSetListener(validationCollector);
			domain.removeResourceSetListener(prePostCommitCollector);
		}
	}
	
	//
	// Fixture methods
	//
	
	@Override
	protected void doSetUp() throws Exception {
		super.doSetUp();
		
		System.out.println("Performance test: " + getName()); //$NON-NLS-1$
		System.out.println("==============================="); //$NON-NLS-1$
		
		timings = new java.util.ArrayList<Long>();
	}
	
	@Override
	protected void doTearDown() throws Exception {
		removeOutliers();
		
		System.out.println("Mean time for " + RUNS + " runs: " + meanTiming()); //$NON-NLS-1$ //$NON-NLS-2$
		System.out.println("Standard deviation: " + stddevTiming()); //$NON-NLS-1$
		
		System.out.println("==============================="); //$NON-NLS-1$
		
		timings = null;
		
		super.doTearDown();
	}
	
	/**
	 * Creates a deeply nested tree of transactions, having the specified
	 * <code>depth</code> and in which every non-leaf transaction has
	 * <code>breadth</code> children.
	 */
	protected void createDeeplyNestedTransaction(int depth, int breadth) {
		// stopping condition
		if (depth-- > 0) {
			startWriting();
			
			if (depth > 0) {
				for (int i = 0; i < breadth; i++) {
					createDeeplyNestedTransaction(depth, breadth);
				}
			}
			
			commit();
		}
	}
	
	/**
	 * Adds a largish number of libraries to the test resource, each with
	 * some books.
	 */
	protected void addStuffToTestResourceAndClose() {
		startWriting();
		
		Date today = new Date();
		
		for (int i = 0; i < 50; i++) {
			addLibraryWithBooks(today);
		}
		
		commit();
		
		startReading();
		
		try {
			testResource.save(Collections.EMPTY_MAP);
		} catch (IOException e) {
			e.printStackTrace();
			fail("Failed to save test resource: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}
		
		testResource.unload();
		
		commit();
	}
	
	protected void addLibraryWithBooks(Date today) {
		Library lib = EXTLibraryFactory.eINSTANCE.createLibrary();
		EList<Library> branches = lib.getBranches();
		
		for (int j = 0; j < 20; j++) {
			Library branch = EXTLibraryFactory.eINSTANCE.createLibrary();
			EList<Book> books = branch.getBooks();
			
			for (int i = 0; i < 10; i++) {
				Book book = EXTLibraryFactory.eINSTANCE.createBook();
	
				book.setTitle("Foo"); //$NON-NLS-1$
				book.setPages(30);
				book.setCategory(BookCategory.BIOGRAPHY_LITERAL);
				book.setCopies(7);
				book.setPublicationDate(today);
				books.add(book);
			}
			
			branches.add(branch);
		}
		
		testResource.getContents().add(lib);
	}
	
	/**
	 * Creates a deeply nested tree of transactions, having the specified
	 * <code>depth</code> and in which every non-leaf transaction has
	 * <code>breadth</code> children.  These transactions actually make changes
	 * at every level.
	 */
	protected void createTreeOfMixedTransactions(
			int depth, int breadth,
			final int[] totalNotifications,
			final int[] expectedPrecommitNotifications,
			final int[] expectedPostcommitNotifications,
			final int[] expectedValidationNotifications) {
		
		createTreeOfMixedTransactions(
				depth, breadth,
				true, true, true,
				totalNotifications,
				expectedPrecommitNotifications,
				expectedPostcommitNotifications,
				expectedValidationNotifications);
	}
	
	/**
	 * Creates a deeply nested tree of transactions, having the specified
	 * <code>depth</code> and in which every non-leaf transaction has
	 * <code>breadth</code> children.  These transactions actually make changes
	 * at every level.
	 */
	protected void createTreeOfMixedTransactions(
			int depth, int breadth,
			boolean trigger, boolean notify, boolean validate,
			final int[] totalNotifications,
			final int[] expectedPrecommitNotifications,
			final int[] expectedPostcommitNotifications,
			final int[] expectedValidationNotifications) {
		
		// stopping condition
		if (depth-- > 0) {
			startWriting(getOptions(trigger, notify, validate));
			
			pokeRoot();
			totalNotifications[0]++;
			if (trigger) {
				expectedPrecommitNotifications[0]++;
			}
			if (notify) {
				expectedPostcommitNotifications[0]++;
			}
			if (validate) {
				expectedValidationNotifications[0]++;
			}
			
			if (depth > 0) {
				for (int i = 0; i < breadth; i++) {
					createTreeOfMixedTransactions(
							depth, breadth,
							trigger && (depth >= 3),
							notify && (depth >= 2),
							validate && (depth >= 1),
							totalNotifications,
							expectedPrecommitNotifications,
							expectedPostcommitNotifications,
							expectedValidationNotifications);
					
					pokeRoot();
					totalNotifications[0]++;
					if (trigger) {
						expectedPrecommitNotifications[0]++;
					}
					if (notify) {
						expectedPostcommitNotifications[0]++;
					}
					if (validate) {
						expectedValidationNotifications[0]++;
					}
				}
			}
			
			commit();
		}
	}
	
	private void pokeRoot() {
		root.setName(Long.toString(System.currentTimeMillis() ^ root.getName().hashCode()));
	}
	
	private static Map<Object, Object> allNotifications;
	private static Map<Object, Object> noTriggers;
	private static Map<Object, Object> validationOnly;
	private static Map<Object, Object> noNotifications;
	static {
		allNotifications = Collections.emptyMap();
		noTriggers = Collections.<Object, Object>singletonMap(
			Transaction.OPTION_NO_TRIGGERS, Boolean.TRUE);
		validationOnly = new java.util.HashMap<Object, Object>(noTriggers);
		validationOnly.put(Transaction.OPTION_NO_NOTIFICATIONS, Boolean.TRUE);
		noNotifications = new java.util.HashMap<Object, Object>(validationOnly);
		noNotifications.put(Transaction.OPTION_NO_VALIDATION, Boolean.TRUE);
	}
	private Map<Object, Object> getOptions(boolean trigger, boolean notify,
			boolean validate) {
		
		if (!trigger) {
			if (!notify) {
				if (!validate) {
					return noNotifications;
				}
				
				return validationOnly;
			}
			
			return noTriggers;
		}
		
		return allNotifications;
	}
	
	final void startClock() {
		clock = System.currentTimeMillis();
	}
	
	final long stopClock() {
		long result = System.currentTimeMillis() - clock;
		
		timings.add(result);
		
		return result;
	}
	
	final void removeOutliers() {
		Collections.sort(timings);
		
		for (int i = 0; i < OUTLIERS / 2; i++) {
			System.out.println("Remove high timing: " + timings.remove(timings.size() - 1)); //$NON-NLS-1$
			System.out.println("Remove low timing : " + timings.remove(0)); //$NON-NLS-1$
		}
	}
	
	final double meanTiming() {
		double result = 0;
		int count = timings.size();
		
		for (int i = 0; i < count; i++) {
			result += timings.get(i).doubleValue();
		}
		
		result /= count;
		
		return result;
	}
	
	final double stddevTiming() {
		double result = 0;
		double mean = meanTiming();
		double dev = 0;
		int count = timings.size();
		
		for (int i = 0; i < count; i++) {
			dev = timings.get(i).doubleValue() - mean;
			
			result += dev * dev;
		}
		
		result = Math.sqrt(result / count);
		
		return result;
	}
}
