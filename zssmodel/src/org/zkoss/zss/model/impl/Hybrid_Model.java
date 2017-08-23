package org.zkoss.zss.model.impl;

import org.model.BlockStore;
import org.model.DBContext;
import org.zkoss.util.Pair;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SSheet;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Hybrid_Model extends RCV_Model {
    BlockStore bs;
    private int block_row = 100000;
    //Tables on the sheet

    private Logger logger = Logger.getLogger(Hybrid_Model.class.getName());
    private MetaDataBlock metaDataBlock;

    // This list is synchronized with modelEntryList in metaDataBlock
    private List<Pair<CellRegion, Model>> tableModels;

    Hybrid_Model(DBContext context, SSheet sheet, String tableName) {
        super(context, sheet, tableName);
        loadMetaData(context);
    }

    public boolean checkOverap(CellRegion cellRegion) {
        return metaDataBlock.modelEntryList
                .stream()
                .filter(e -> e.range.overlaps(cellRegion))
                .findFirst().isPresent();
    }

    private void loadMetaData(DBContext context) {
        tableModels = new ArrayList<>();
        bs = new BlockStore(context, tableName + "_hb_meta");
        metaDataBlock = bs.getObject(context, 0, MetaDataBlock.class);
        if (metaDataBlock == null) {
            metaDataBlock = new MetaDataBlock();
            bs.putObject(0, metaDataBlock);
            bs.flushDirtyBlocks(context);
        } else {
            // Create models
            metaDataBlock.modelEntryList
                    .stream()
                    .sequential()
                    .forEach(e -> tableModels.add(
                            new Pair<>(e.range,
                                    Model.CreateModel(context, sheet, e.modelType, e.tableName))));
            tableModels.stream()
                    .filter(e -> e.y instanceof TOM_Model)
                    .forEach(e ->
                            TOM_Mapping.instance.addMapping(e.y.tableName, (TOM_Model) e.y,
                                    new RefImpl(sheet.getBook().getId(),
                                            sheet.getSheetName(), e.x.getRow(), e.x.getColumn(),
                                            e.x.getLastRow(), e.x.getLastColumn())));
        }
    }

    /* For All the TOM models that display the range adjust the rnage */
    public void shrinkRange(String tableName) {

    }

    @Override
    public void dropSchema(DBContext context) {
        tableModels.stream().forEach(e -> e.y.dropSchema(context));
        super.dropSchema(context);
        bs.dropSchemaAndClear(context);
    }

    /* Fix later
    public void converttoRCV(DBContext context, CellRegion range) {
        for (int i = range.getLastRow() / block_row + 1; i > 0; i--) {
            int min_row = range.getRow() + (i - 1) * block_row;
            int max_row = range.getRow() + i * block_row;
            if (i > range.getLastRow() / block_row) max_row = range.getLastRow();
            CellRegion work_range = new CellRegion(min_row, range.getColumn(), max_row, range.getLastColumn());
            Collection<AbstractCellAdv> cells = getCells(context, work_range)
                    .stream()
                    //.map(e -> e.shiftedCell(-range.getRow(), -range.getColumn())) // Translate
                    .collect(Collectors.toList());
            // logger.info("Done - Loading Cells in Range " + work_range.toString());

            // Do a RCV delete
            deleteCells(context, work_range);
            //logger.info("Done - Delete from RCV in Range " + work_range.toString());
            // Insert data into new table
            super.updateCellsCopy(context, cells);
            // model.updateCellsCopy(context, cells);
            //  logger.info("Done - Insert into new model in CellRegion " + work_range.toString());
        }


    } */

    //Construct rom_table on the selected range get from getRomtables
    // Convert to ROM or COM.

    public boolean convertToROM(DBContext context, ModelType modelType, CellRegion range, String name) {
        logger.info("Start - Converting HYBRID " + range.toString());

        //create a new rom_model or tom_model
        // Need to update to a better way to handle delete tables
        String newTableName;
        Model newModel;

        newTableName = this.tableName + "_" + Integer.toHexString(++metaDataBlock.romTableIdentifier);
        newModel = Model.CreateModel(context, sheet, modelType, newTableName);
        newModel.insertRows(context, 0, range.getLastRow() - range.getRow() + 1);

        // Migrate data to the new table
        newModel.insertCols(context, 0, range.getLastColumn() - range.getColumn() + 1);

        // logger.info("Start - Loading Cells");

        // Work in small range
        for (int i = range.getLastRow() / block_row + 1; i > 0; i--) {
            int min_row = range.getRow() + (i - 1) * block_row;
            int max_row = range.getRow() + i * block_row;
            if (i > range.getLastRow() / block_row) max_row = range.getLastRow();
            CellRegion work_range = new CellRegion(min_row, range.getColumn(), max_row, range.getLastColumn());

            Collection<AbstractCellAdv> cells = getCells(context, work_range)
                    .stream()
                    .peek(e -> e.translate(-range.getRow(), -range.getColumn())) // Translate
                    .collect(Collectors.toList());

            // Do a RCV delete
            deleteCells(context, work_range);
            // Insert data into new table
//             model.updateCellsCopy(context, cells);
            newModel.updateCells(context, cells);
        }

        if (modelType == ModelType.TOM_Model)
            --range.row;

        // Collect a list of models to be deleted
        linkTable(context, newTableName, range);
//       super.vaccumTable(context);

        return true;
    }

    public boolean createTable(DBContext context, CellRegion range, String tableName) {
        String newTableName;

        newTableName = tableName.toLowerCase();
        /* First create table then create model */
        CellRegion tableHeaderRow = new CellRegion(range.getRow(), range.getColumn(), range.getRow(), range.getLastColumn());
        List<String> columnList = getCells(context, tableHeaderRow)
                .stream()
                .sorted(Comparator.comparingInt(SCell::getRowIndex))
                .map(AbstractCellAdv::getValue)
                .map(Object::toString)
                .collect(Collectors.toList());

        /* assume first column is key */
        String createTable = (new StringBuilder())
                .append("CREATE TABLE IF NOT EXISTS ")
                .append(newTableName)
                .append(" (")
                .append(columnList.stream().map(e -> e + " TEXT").collect(Collectors.joining(",")))
                .append(") WITH OIDS")
                .toString();

        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute(createTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        deleteCells(context, tableHeaderRow);
        return true;
    }

    public List<Integer> insertTuples(DBContext context, CellRegion range, String tableName) {

        List<Integer> oidList = new ArrayList<>();
        // Delete Header

        //++range.row;

        // Migrate data to the new table
        //newModel.insertCols(context, 0, range.getLastColumn() - range.getColumn() + 1);
        int columnCount = range.getLastColumn() - range.getColumn() + 1;
        // logger.info("Start - Loading Cells");

        String update = new StringBuffer("INSERT INTO ")
                .append(tableName)
                .append(" VALUES (")
                .append(IntStream.range(0, columnCount).mapToObj(e -> "?").collect(Collectors.joining(",")))
                .append(") RETURNING oid;")
                .toString();

        // Start min_row  from 1.
        try (PreparedStatement stmt = context.getConnection().prepareStatement(update)) {
            // Work in small range
            for (int i = range.getLastRow() / block_row + 1; i > 0; i--) {
                int min_row = range.getRow() + (i - 1) * block_row;
                int max_row = range.getRow() + i * block_row;
                if (i > range.getLastRow() / block_row) max_row = range.getLastRow();
                CellRegion work_range = new CellRegion(min_row, range.getColumn(), max_row, range.getLastColumn());


                Collection<AbstractCellAdv> cells = getCells(context, work_range)
                        .stream()
                        .peek(e -> e.translate(-range.getRow(), -range.getColumn())) // Translate
                        .collect(Collectors.toList());

                // Gather cells of same row together
                SortedMap<Integer, SortedMap<Integer, AbstractCellAdv>> groupedCells = new TreeMap<>();
                for (AbstractCellAdv cell : cells) {
                    SortedMap<Integer, AbstractCellAdv> _row = groupedCells.get(cell.getRowIndex());
                    if (_row == null) {
                        _row = new TreeMap<>();
                        groupedCells.put(cell.getRowIndex(), _row);
                    }
                    _row.put(cell.getColumnIndex(), cell);
                }

                for (SortedMap<Integer, AbstractCellAdv> tuple : groupedCells.values()) {
                    for (int j = 0; j < columnCount; j++) {
                        if (tuple.containsKey(j))
                            stmt.setString(j + 1,
                                    tuple.get(j).getValue().toString());
                        else
                            stmt.setNull(j + 1, Types.VARCHAR);
                    }

                    ResultSet resultSet = stmt.executeQuery();
                    while (resultSet.next())
                        oidList.add(resultSet.getInt(1));
                    resultSet.close();

                }
                super.deleteCells(context, work_range);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Collect a list of models to be deleted

//       super.vaccumTable(context);

        return oidList;
    }

    public CellRegion linkTable(DBContext context, String tableName, CellRegion range) {
        TOM_Model model = (TOM_Model) Model.CreateModel(context, sheet, ModelType.TOM_Model, tableName);
        model.createOIDIndex(context);
        model.indexOIDs(context);
        range = model.getBounds(context).shiftedRange(range.row, range.column).getOverlap(range);
        TOM_Mapping.instance.addMapping(tableName, model, new RefImpl(sheet.getBook().getId(),
                sheet.getSheetName(), range.getRow(), range.getColumn(),
                range.getLastRow(), range.getLastColumn()));

        tableModels.add(new Pair<>(range, model));
        MetaDataBlock.ModelEntry modelEntry = new MetaDataBlock.ModelEntry();
        modelEntry.range = range;
        modelEntry.modelType = ModelType.TOM_Model;
        modelEntry.tableName = model.getTableName();
        metaDataBlock.modelEntryList.add(modelEntry);
        bs.putObject(0, metaDataBlock);
        bs.flushDirtyBlocks(context);
        return range;
    }

    @Override
    public void insertRows(DBContext context, int row, int count) {
        //TODO: Max Values ...
        CellRegion rowRange = new CellRegion(row, 1, row, Integer.MAX_VALUE);
        for (int i = 0; i < metaDataBlock.modelEntryList.size(); ++i) {
            CellRegion tableRange = metaDataBlock.modelEntryList.get(i).range;
            Model tableModel = tableModels.get(i).y;
            if (tableRange.overlaps(rowRange)) {
                if (!(tableModel instanceof TOM_Model)) {
                    tableModel.insertRows(context, row - tableRange.getRow(), count);
                    // Extend range
                    metaDataBlock.modelEntryList.get(i).range = tableRange.extendRange(count, 0);
                }
            } else if (tableRange.getRow() > row) {
                // Shift tables
                metaDataBlock.modelEntryList.get(i).range = tableRange.shiftedRange(count, 0);
            }
        }
        bs.putObject(0, metaDataBlock);
        bs.flushDirtyBlocks(context);

        super.insertRows(context, row, count);
    }

    @Override
    public void insertCols(DBContext context, int col, int count) {
        CellRegion colRange = new CellRegion(1, col, Integer.MAX_VALUE, col);
        for (int i = 0; i < metaDataBlock.modelEntryList.size(); ++i) {
            CellRegion tableRange = metaDataBlock.modelEntryList.get(i).range;
            Model tableModel = tableModels.get(i).y;
            if (tableRange.overlaps(colRange)) {
                if (!(tableModel instanceof TOM_Model)) {
                    tableModel.insertCols(context, col - tableRange.getColumn(), count);
                    // Extend range
                    metaDataBlock.modelEntryList.get(i).range = tableRange.extendRange(0, count);
                }
            } else if (tableRange.getColumn() > col) {
                // Shift tables
                metaDataBlock.modelEntryList.get(i).range = tableRange.shiftedRange(0, count);
            }
        }
        bs.putObject(0, metaDataBlock);
        bs.flushDirtyBlocks(context);
        super.insertCols(context, col, count);
    }


    @Override
    public void deleteRows(DBContext context, int row, int count) {
        CellRegion rowRange = new CellRegion(row, 0, row + count - 1, Integer.MAX_VALUE);
        int i = 0;
        while (i < metaDataBlock.modelEntryList.size()) {
            CellRegion tableRange = metaDataBlock.modelEntryList.get(i).range;
            Model tableModel = tableModels.get(i).y;
            if (rowRange.contains(tableRange)) {
                // Delete table
                tableModel.dropSchema(context);
                metaDataBlock.modelEntryList.remove(i);
                tableModels.remove(i);
                //Continue next table at some i.
                continue;
            } else if (tableRange.overlaps(rowRange)) {
                if (!(tableModel instanceof TOM_Model)) {
                    CellRegion intersection = tableRange.getOverlap(rowRange);
                    tableModel.deleteRows(context, intersection.getRow(), intersection.getHeight());
                    metaDataBlock.modelEntryList.get(i).range = tableRange.extendRange(-intersection.getHeight(), 0);
                }
            } else if (tableRange.getRow() > row) {
                // Shift tables
                metaDataBlock.modelEntryList.get(i).range = tableRange.shiftedRange(-count, 0);
            }
            ++i;
        }
        bs.putObject(0, metaDataBlock);
        bs.flushDirtyBlocks(context);

        super.deleteRows(context, row, count);
    }

    @Override
    public boolean deleteTuples(DBContext context, CellRegion cellRegion) {
        List<Pair<CellRegion, Model>> modelEntries = tableModels.stream()
                .filter(e -> e.x.overlaps(cellRegion))
                .collect(Collectors.toList());

        if (modelEntries.size() != 1)
            return false;

        if (!(modelEntries.get(0).y instanceof TOM_Model))
            return false;


        CellRegion tableRegion = modelEntries.get(0).x;

        if (!tableRegion.contains(cellRegion))
            return false;

        TOM_Model tomModel = (TOM_Model) modelEntries.get(0).y;
        /* -1 below as top row is header */
        tomModel.deleteTuples(context, cellRegion.getRow() - tableRegion.getRow() - 1, cellRegion.getHeight());
        // For all the regions that are displaying more than my displayed records
        // Reduce the size.

        shrinkToBound(context, tomModel);

        TOM_Mapping.instance.pushUpdates(context, tomModel.getTableName());
        return true;
    }


    @Override
    public void deleteCols(DBContext context, int col, int count) {
        CellRegion colRange = new CellRegion(0, col, Integer.MAX_VALUE, col + count - 1);
        int i = 0;
        while (i < metaDataBlock.modelEntryList.size()) {
            CellRegion tableRange = metaDataBlock.modelEntryList.get(i).range;
            Model tableModel = tableModels.get(i).y;
            if (colRange.contains(tableRange)) {
                // Delete table
                tableModel.dropSchema(context);
                metaDataBlock.modelEntryList.remove(i);
                tableModels.remove(i);
                //Continue next table at some i.
                continue;
            } else if (tableRange.overlaps(colRange)) {
                if (!(tableModel instanceof TOM_Model)) {
                    CellRegion intersection = tableRange.getOverlap(colRange);
                    tableModel.deleteCols(context, intersection.getColumn(), intersection.getLength());
                    metaDataBlock.modelEntryList.get(i).range = tableRange.extendRange(0, -intersection.getLength());
                }
            } else if (tableRange.getColumn() > col) {
                // Shift tables
                metaDataBlock.modelEntryList.get(i).range = tableRange.shiftedRange(0, -count);
            }
            ++i;


        }
        bs.putObject(0, metaDataBlock);
        bs.flushDirtyBlocks(context);

        super.deleteCols(context, col, count);
    }

    @Override
    public void updateCells(DBContext context, Collection<AbstractCellAdv> cells) {
        Set<AbstractCellAdv> pendingCells = new HashSet<>(cells);
        for (int i = 0; i < metaDataBlock.modelEntryList.size(); ++i) {
            CellRegion tableRange = metaDataBlock.modelEntryList.get(i).range;
            List<AbstractCellAdv> cellsInRange = pendingCells
                    .stream()
                    .filter(c -> tableRange.contains(c.getRowIndex(), c.getColumnIndex()))
                    .collect(Collectors.toList());
            pendingCells.removeAll(cellsInRange);

            // Translate
            if (!cellsInRange.isEmpty()) {
                cellsInRange.stream()
                        .forEach(e -> e.translate(-tableRange.getRow(), -tableRange.getColumn()));
                tableModels.get(i).y.updateCells(context, cellsInRange);
                cellsInRange.stream()
                        .forEach(e -> e.translate(tableRange.getRow(), tableRange.getColumn()));
            }

        }
        super.updateCells(context, pendingCells);
    }

    @Override
    public void deleteCells(DBContext context, CellRegion range) {
        IntStream.range(0, metaDataBlock.modelEntryList.size())
                .filter(e -> metaDataBlock.modelEntryList.get(e).range.overlaps(range))
                .forEach(e -> deleteCells(context, range.getOverlap(metaDataBlock.modelEntryList.get(e).range)
                        .shiftedRange(
                                -metaDataBlock.modelEntryList.get(e).range.getRow(),
                                -metaDataBlock.modelEntryList.get(e).range.getColumn())));
        super.deleteCells(context, range);
    }

    @Override
    public void deleteCells(DBContext context, Collection<AbstractCellAdv> cells) {
        Set<AbstractCellAdv> pendingCells = new HashSet<>(cells);
        for (int i = 0; i < metaDataBlock.modelEntryList.size(); ++i) {
            CellRegion tableRange = metaDataBlock.modelEntryList.get(i).range;
            List<AbstractCellAdv> cellsInRange = pendingCells
                    .stream()
                    .filter(c -> tableRange.contains(c.getRowIndex(), c.getColumnIndex()))
                    .collect(Collectors.toList());
            tableModels.get(i).y.deleteCells(context, cellsInRange.stream()
                    .peek(e -> e.translate(-tableRange.getRow(), -tableRange.getLastColumn()))
                    .collect(Collectors.toList()));
            pendingCells.removeAll(cellsInRange);
        }
        super.deleteCells(context, pendingCells);
    }

    @Override
    public Collection<AbstractCellAdv> getCells(DBContext context, CellRegion range) {
        List<AbstractCellAdv> cells = new ArrayList<>();

        // System.out.println("size" + metaDataBlock.modelEntryList.size());
        //System.out.println("Queries " + cnt);

        IntStream.range(0, metaDataBlock.modelEntryList.size())
                .filter(e -> metaDataBlock.modelEntryList.get(e).range.overlaps(range))
                .filter(e -> metaDataBlock.modelEntryList.get(e).modelType != ModelType.TOM_Model)
                .forEach(e -> cells.addAll(
                        tableModels.get(e).y
                                .getCells(context, metaDataBlock.modelEntryList.get(e).range.getOverlap(range)
                                        .shiftedRange(
                                                -metaDataBlock.modelEntryList.get(e).range.getRow(),
                                                -metaDataBlock.modelEntryList.get(e).range.getColumn()))
                                .stream()
                                .peek(c -> c.translate(metaDataBlock.modelEntryList.get(e).range.getRow(),
                                        metaDataBlock.modelEntryList.get(e).range.getColumn()))
                                .collect(Collectors.toList())));

        /* For a TOM model call a sepecial function */
        IntStream.range(0, metaDataBlock.modelEntryList.size())
                .filter(e -> metaDataBlock.modelEntryList.get(e).range.overlaps(range))
                .filter(e -> metaDataBlock.modelEntryList.get(e).modelType == ModelType.TOM_Model)
                .forEach(e -> cells.addAll(
                        ((TOM_Model) tableModels.get(e).y)
                                .getCellsTOM(context, sheet,
                                        metaDataBlock.modelEntryList.get(e).range.getOverlap(range)
                                                .shiftedRange(
                                                        -metaDataBlock.modelEntryList.get(e).range.getRow(),
                                                        -metaDataBlock.modelEntryList.get(e).range.getColumn()))
                                .stream()
                                .peek(c -> c.translate(metaDataBlock.modelEntryList.get(e).range.getRow(),
                                        metaDataBlock.modelEntryList.get(e).range.getColumn()))
                                .collect(Collectors.toList())));

        boolean encompass = false;
        for (MetaDataBlock.ModelEntry m : metaDataBlock.modelEntryList)
            if (m.range.contains(range))
                encompass = true;

        if (encompass == false) {
            cells.addAll(super.getCells(context, range));
        }
        return Collections.unmodifiableCollection(cells);
    }

    public Collection<CellRegion> getTablesRanges() {
        return metaDataBlock.modelEntryList.stream().map(e -> e.range).collect(Collectors.toList());
    }

    @Override
    public void clearCache(DBContext context) {
        super.clearCache(context);
        loadMetaData(context);
    }

    public void deleteModel(DBContext dbContext, CellRegion modelRange) {
        metaDataBlock.modelEntryList.removeIf(e -> e.range.equals(modelRange));
    }

    public Pair<CellRegion, Model> getTableModelAbove(CellRegion newTuplesRegion) {
        // Make sure this range is not contained within any other table.
        if (tableModels.stream()
                .map(e -> e.x)
                .filter(e -> checkOverap(newTuplesRegion))
                .findFirst().isPresent())
            return null;

        for (Pair<CellRegion, Model> cellRegionModelPair : tableModels) {
            if (newTuplesRegion.getRow() == cellRegionModelPair.x.getLastRow() + 1 &&
                    newTuplesRegion.getColumn() == cellRegionModelPair.x.getColumn() &&
                    newTuplesRegion.getLastColumn() == cellRegionModelPair.x.getLastColumn() &&
                    cellRegionModelPair.y instanceof TOM_Model)
                return cellRegionModelPair;
        }
        return null;
    }

    public void shrinkToBound(DBContext dbContext, TOM_Model tomModel) {
        CellRegion bounds = tomModel.getBounds(dbContext);
        for (int i = 0; i < tableModels.size(); i++) {
            if (tableModels.get(i).y.getTableName() == tomModel.getTableName()) {
                boolean changed = false;
                CellRegion originalRegion = tableModels.get(i).x;
                int newLastRow = originalRegion.getLastRow();
                int newLastCol = originalRegion.getLastColumn();

                CellRegion shiftedRange = originalRegion.shiftedRange(-originalRegion.getRow(),
                        -originalRegion.getColumn());
                if (shiftedRange.getLastRow() >= bounds.getLastRow()) {
                    newLastRow = bounds.getLastRow() + originalRegion.getRow();
                    changed = true;
                }

                if (shiftedRange.getLastColumn() >= bounds.getLastColumn()) {
                    newLastCol = bounds.getLastColumn() + originalRegion.getColumn();
                    changed = true;
                }


                if (changed) {
                    CellRegion newRegion = new CellRegion(originalRegion.getRow(),
                            originalRegion.getColumn(),
                            newLastRow, newLastCol);

                    tableModels.remove(i);
                    tableModels.add(i, new Pair<>(newRegion, tomModel));
                    MetaDataBlock.ModelEntry modelEntry = new MetaDataBlock.ModelEntry();
                    modelEntry.range = newRegion;
                    modelEntry.modelType = ModelType.TOM_Model;
                    modelEntry.tableName = tomModel.getTableName();
                    metaDataBlock.modelEntryList.remove(i);
                    metaDataBlock.modelEntryList.add(i, modelEntry);
                    bs.putObject(0, metaDataBlock);
                    bs.flushDirtyBlocks(dbContext);


                    TOM_Mapping.instance.updateRegion(tomModel,
                            sheet.getBook().getId(), sheet.getSheetName(), originalRegion, newRegion);
                    sheet.clearCache(originalRegion);
                }
            }
        }
        TOM_Mapping.instance.pushUpdates(dbContext, tomModel.getTableName());
    }

    // Extend the area of x by cellRegion
    public void extendRange(DBContext dbContext, String tableName, CellRegion oldRegion, CellRegion extendRegion) {
        CellRegion newRegion = new CellRegion(oldRegion.getRow(), oldRegion.getColumn(),
                extendRegion.getLastRow(), extendRegion.getLastColumn());

        for (int i = 0; i < tableModels.size(); i++) {
            if (tableModels.get(i).x == oldRegion) {
                TOM_Model model = (TOM_Model) tableModels.get(i).y;
                tableModels.remove(i);

                tableModels.add(new Pair<>(newRegion, model));
                MetaDataBlock.ModelEntry modelEntry = new MetaDataBlock.ModelEntry();
                modelEntry.range = newRegion;
                modelEntry.modelType = ModelType.TOM_Model;
                modelEntry.tableName = model.getTableName();
                metaDataBlock.modelEntryList.remove(i);
                metaDataBlock.modelEntryList.add(modelEntry);
                bs.putObject(0, metaDataBlock);
                bs.flushDirtyBlocks(dbContext);

                TOM_Mapping.instance.updateRegion(model, sheet.getBook().getId(), sheet.getSheetName(),
                        oldRegion, newRegion);
                TOM_Mapping.instance.pushUpdates(dbContext, model.getTableName());
                return;
            }
        }

    }


    private static class MetaDataBlock {
        List<ModelEntry> modelEntryList;
        int romTableIdentifier;

        MetaDataBlock() {
            modelEntryList = new ArrayList<>();
            romTableIdentifier = 1;
        }

        private static class ModelEntry {
            CellRegion range;
            String tableName;
            ModelType modelType;
            String orderName;   // Identify an order based for the table.
        }
    }
}