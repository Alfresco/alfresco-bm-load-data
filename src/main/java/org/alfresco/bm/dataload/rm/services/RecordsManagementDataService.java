package org.alfresco.bm.dataload.rm.services;

import java.util.List;

import org.alfresco.bm.data.DataCreationState;

/**
 * Access of Records Management file plan
 * 
 * @author Frank Becker
 * @since 2.6
 */
public interface RecordsManagementDataService
{
    /**
     * Returns the file plan by it's unique name.
     * 
     * @param filePlanName
     *            (String) name of the file plan to retrieve.
     * 
     * @return (FilePlanData) file plan by it's unique name.
     * 
     * @throws RecordsManagementBenchmarkException
     *             if file plan not available or error
     */
    FilePlanData getFilePlan(String filePlanName) throws RecordsManagementBenchmarkException;

    /**
     * Returns the default file plan.
     * 
     * @return (FilePlanData) default file plan.
     * 
     * @throws RecordsManagementBenchmarkException
     *             if no default file plan available or error
     */
    FilePlanData getDefaultFilePlan() throws RecordsManagementBenchmarkException;

    /**
     * Returns a random file plan data object.
     * 
     * @return (FilePlanData) random file plan data object.
     * 
     * @throws RecordsManagementBenchmarkException
     *             if no file plan available or error
     */
    FilePlanData getRandomFilePlan() throws RecordsManagementBenchmarkException;

    /**
     * Adds a file plan.
     * 
     * @param data
     *            (FilePlanData, required) file plan to add.
     */
    void addFilePlan(FilePlanData data);

    /**
     * Returns the total number of file plans independent from the creation status.
     * 
     * @return the total number of file plans independent from the creation status.
     */
    long countFilePlans();

    /**
     * Returns the number of file plans with the given creations state.
     * 
     * @param creationState
     *            (DataCreationState, optional) creation state, if null number of all file plans will be returned.
     * 
     * @return number of file plans with the given creations state.
     */
    long countFilePlans(DataCreationState creationState);

    /**
     * Returns all file plans in a collection.
     * 
     * @return (List<FilePlanData>) all file plans in a collection.
     */
    List<FilePlanData> getFilePlans();

    /**
     * Returns all file plans of a given creation state in a collection.
     * 
     * @param creationState
     *            (DataCreationState, optional) creation state, if null all file plans will be returned.
     * 
     * @return (List<FilePlanData>) all file plans of a given creation state in a collection.
     */
    List<FilePlanData> getFilePlans(DataCreationState creationState);

    /**
     * Returns the record category in a file plan by name.
     * 
     * @param categoryName
     *            (String) name of the record category to retrieve.
     * 
     * @return (RecordCategoryData) the record category in a file plan by name.
     * 
     * @throws RecordsManagementBenchmarkException
     *             if record category not available or error
     */
    RecordCategoryData getRecordCategory(String categoryName) throws RecordsManagementBenchmarkException;

    /**
     * Returns a random record category.
     * 
     * @return (RecordCategoryData) random record category.
     * 
     * @throws RecordsManagementBenchmarkException
     *             if no record category available or error
     */
    RecordCategoryData getRandomRecordCategory();

    /**
     * Adds a record category to MongoDB.
     * 
     * @param recordCategory
     *            (RecordCategoryData, required) record category to add.
     */
    void addRecordCategory(RecordCategoryData recordCategory);

    /**
     * Counts the number of record categories independent from the creation status - returns the number of all known
     * record categories.
     * 
     * @return the number of all known record categories
     */
    long countRecordCategories();

    /**
     * Returns the number of record categories with the given creations state.
     * 
     * @param creationState
     *            (DataCreationState, required) creation state.
     * 
     * @return number of record categories with the given creations state.
     */
    long countRecordCategories(DataCreationState creationState);

    /**
     * Returns all record categories.
     * 
     * @return (List<RecordCategoryData>) all record categories.
     */
    List<RecordCategoryData> getRecordCategories();

    /**
     * Returns all record categories of the chosen data category.
     * 
     * @param creationState
     *            (DataCreationState, optional) data creation state or null to return all record categories.
     * 
     * @return (List<RecordCategoryData>) all record categories of the chosen data category.
     */
    List<RecordCategoryData> getRecordCategories(DataCreationState creationState);
}
