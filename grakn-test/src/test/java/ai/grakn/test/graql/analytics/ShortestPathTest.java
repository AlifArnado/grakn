package ai.grakn.test.graql.analytics;

import ai.grakn.GraknGraph;
import ai.grakn.GraknGraphFactory;
import ai.grakn.concept.Concept;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Entity;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.RelationType;
import ai.grakn.concept.RoleType;
import ai.grakn.exception.GraknValidationException;
import ai.grakn.graph.internal.computer.GraknSparkComputer;
import ai.grakn.graql.ComputeQuery;
import ai.grakn.graql.Graql;
import ai.grakn.graql.internal.analytics.GraknVertexProgram;
import ai.grakn.test.EngineContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static ai.grakn.test.GraknTestEnv.usingOrientDB;
import static ai.grakn.test.GraknTestEnv.usingTinker;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;

public class ShortestPathTest {
    private static final String thing = "thing";
    private static final String anotherThing = "anotherThing";
    private static final String related = "related";
    private static final String veryRelated = "veryRelated";

    private ConceptId entityId1;
    private ConceptId entityId2;
    private ConceptId entityId3;
    private ConceptId entityId4;
    private ConceptId entityId5;
    private ConceptId relationId12;
    private ConceptId relationId13;
    private ConceptId relationId24;
    private ConceptId relationId34;
    private ConceptId relationId1A12;

    public GraknGraphFactory factory;

    @ClassRule
    public static EngineContext rule = EngineContext.startInMemoryServer();

    @Before
    public void setUp() {
        // TODO: Fix tests in orientdb
        assumeFalse(usingOrientDB());

        factory = rule.factoryWithNewKeyspace();

        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(GraknVertexProgram.class);
        logger.setLevel(Level.DEBUG);

        logger = (Logger) org.slf4j.LoggerFactory.getLogger(ComputeQuery.class);
        logger.setLevel(Level.DEBUG);
    }

    @After
    public void close() {
        GraknSparkComputer.clear();
    }

    @Test(expected = IllegalStateException.class)
    public void testShortestPathExceptionIdNotFound() throws Exception {
        // TODO: Fix in TinkerGraphComputer
        assumeFalse(usingTinker());

        // test on an empty graph
        try(GraknGraph graph = factory.getGraph()) {
            graph.graql().compute().path().from(entityId1).to(entityId2).execute();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testShortestPathExceptionIdNotFoundSubgraph() throws Exception {
        // TODO: Fix in TinkerGraphComputer
        assumeFalse(usingTinker());

        addOntologyAndEntities();
        try(GraknGraph graph = factory.getGraph()) {
            graph.graql().compute().path().from(entityId1).to(entityId4).in(thing, related).execute();
        }
    }

    @Test//(expected = RuntimeException.class)
    public void testShortestPathExceptionPathNotFound() throws Exception {
        // TODO: Fix in TinkerGraphComputer
        assumeFalse(usingTinker());

        addOntologyAndEntities();
        try(GraknGraph graph = factory.getGraph()) {
            assertFalse(graph.graql().compute().path().from(entityId1).to(entityId5).execute().isPresent());
        }
    }

    @Test
    public void testShortestPath() throws Exception {
        // TODO: Fix in TinkerGraphComputer
        assumeFalse(usingTinker());

        List<String> correctPath;
        List<String> result;
        addOntologyAndEntities();

        try(GraknGraph graph = factory.getGraph()) {
            // directly connected vertices
            correctPath = Lists.newArrayList(entityId1.getValue(), relationId12.getValue());
            result = graph.graql().compute().path().from(entityId1).to(relationId12).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }
            Collections.reverse(correctPath);
            result = Graql.compute().withGraph(graph).path().to(entityId1).from(relationId12).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }

            // entities connected by a relation
            correctPath = Lists.newArrayList(entityId1.getValue(), relationId12.getValue(), entityId2.getValue());
            result = graph.graql().compute().path().from(entityId1).to(entityId2).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }
            Collections.reverse(correctPath);
            result = graph.graql().compute().path().to(entityId1).from(entityId2).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }

            // only one path exists with given subtypes
            correctPath = Lists.newArrayList(entityId2.getValue(), relationId12.getValue(), entityId1.getValue(), relationId13.getValue(), entityId3.getValue());
            result = Graql.compute().withGraph(graph).path().to(entityId3).from(entityId2).in(thing, related).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }
            Collections.reverse(correctPath);
            result = graph.graql().compute().path().in(thing, related).to(entityId2).from(entityId3).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }

            correctPath = Lists.newArrayList(entityId1.getValue(), relationId12.getValue(), entityId2.getValue());
            result = graph.graql().compute().path().in(thing, related).to(entityId2).from(entityId1).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }
            Collections.reverse(correctPath);
            result = graph.graql().compute().path().in(thing, related).from(entityId2).to(entityId1).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }
        }
    }

    @Test
    public void testShortestPathCastingWithThreeMessages() throws Exception {
        // TODO: Fix in TinkerGraphComputer
        assumeFalse(usingTinker());

        List<String> correctPath;
        List<String> result;
        addOntologyAndEntities2();

        try(GraknGraph graph = factory.getGraph()) {
            correctPath = Lists.newArrayList(entityId2.getValue(), relationId12.getValue(), entityId1.getValue(), relationId13.getValue(), entityId3.getValue());
            result = graph.graql().compute().path().from(entityId2).to(entityId3).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }
            Collections.reverse(correctPath);
            result = graph.graql().compute().path().to(entityId2).from(entityId3).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }

            correctPath = Lists.newArrayList(relationId1A12.getValue(), entityId1.getValue(), relationId13.getValue(), entityId3.getValue());
            result = graph.graql().compute().path().from(relationId1A12).to(entityId3).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }
            Collections.reverse(correctPath);
            result = graph.graql().compute().path().to(relationId1A12).from(entityId3).execute()
                    .get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());
            assertEquals(correctPath.size(), result.size());
            for (int i = 0; i < result.size(); i++) {
                assertEquals(correctPath.get(i), result.get(i));
            }
        }
    }

    @Test
    public void testMultipleIndependentShortestPaths() throws GraknValidationException {
        assumeFalse(usingTinker());
        Set<List<ConceptId>> validPaths = new HashSet<>();
        ConceptId startId;
        ConceptId endId;

        try(GraknGraph graph = factory.getGraph()) {
            EntityType entityType = graph.putEntityType(thing);

            RoleType role1 = graph.putRoleType("role1");
            RoleType role2 = graph.putRoleType("role2");
            entityType.playsRole(role1).playsRole(role2);
            RelationType relationType = graph.putRelationType(related).hasRole(role1).hasRole(role2);

            Entity start = entityType.addEntity();
            Entity end = entityType.addEntity();

            startId = start.getId();
            endId = end.getId();

            // create N identical length paths
            int numberOfPaths = 10;

            for (int i = 0; i < numberOfPaths; i++) {

                List<ConceptId> validPath = new ArrayList<>();
                validPath.add(startId);

                Entity middle = entityType.addEntity();
                ConceptId middleId = middle.getId();
                ConceptId assertion1 = relationType.addRelation()
                        .putRolePlayer(role1, start)
                        .putRolePlayer(role2, middle).getId();

                validPath.add(assertion1);
                validPath.add(middleId);

                ConceptId assertion2 = relationType.addRelation()
                        .putRolePlayer(role1, middle)
                        .putRolePlayer(role2, end).getId();

                validPath.add(assertion2);
                validPath.add(endId);
                validPaths.add(validPath);
            }

            graph.commitOnClose();
        }

        try(GraknGraph graph = factory.getGraph()) {
            Optional<List<Concept>> result = graph.graql().compute().path().from(startId).to(endId).execute();
            assertEquals(1, validPaths.stream().filter(path -> checkPathsAreEqual(path, result)).count());
        }
    }

    private boolean checkPathsAreEqual(List<ConceptId> correctPath, Optional<List<Concept>> computedPath) {
        if (computedPath.isPresent()) {
            List<Concept> actualPath = computedPath.get();
            if (actualPath.isEmpty()) {
                return correctPath.isEmpty();
            } else {
                ListIterator<Concept> elements = actualPath.listIterator();
                boolean returnState = true;
                while (elements.hasNext()) {
                    returnState &= (correctPath.get(elements.nextIndex()).equals(elements.next().getId()));
                }
                return returnState;
            }
        } else {
            return correctPath.isEmpty();
        }
    }

    private void addOntologyAndEntities() throws GraknValidationException {
        try(GraknGraph graph = factory.getGraph()) {
            EntityType entityType1 = graph.putEntityType(thing);
            EntityType entityType2 = graph.putEntityType(anotherThing);

            Entity entity1 = entityType1.addEntity();
            Entity entity2 = entityType1.addEntity();
            Entity entity3 = entityType1.addEntity();
            Entity entity4 = entityType2.addEntity();
            Entity entity5 = entityType1.addEntity();

            entityId1 = entity1.getId();
            entityId2 = entity2.getId();
            entityId3 = entity3.getId();
            entityId4 = entity4.getId();
            entityId5 = entity5.getId();

            RoleType role1 = graph.putRoleType("role1");
            RoleType role2 = graph.putRoleType("role2");
            entityType1.playsRole(role1).playsRole(role2);
            entityType2.playsRole(role1).playsRole(role2);
            RelationType relationType = graph.putRelationType(related).hasRole(role1).hasRole(role2);

            relationId12 = relationType.addRelation()
                    .putRolePlayer(role1, entity1)
                    .putRolePlayer(role2, entity2).getId();
            relationId13 = relationType.addRelation()
                    .putRolePlayer(role1, entity1)
                    .putRolePlayer(role2, entity3).getId();
            relationId24 = relationType.addRelation()
                    .putRolePlayer(role1, entity2)
                    .putRolePlayer(role2, entity4).getId();
            relationId34 = relationType.addRelation()
                    .putRolePlayer(role1, entity3)
                    .putRolePlayer(role2, entity4).getId();

            graph.commitOnClose();
        }
        GraknSparkComputer.clear();
    }

    private void addOntologyAndEntities2() throws GraknValidationException {
        try(GraknGraph graph = factory.getGraph()) {
            EntityType entityType = graph.putEntityType(thing);

            Entity entity1 = entityType.addEntity();
            Entity entity2 = entityType.addEntity();
            Entity entity3 = entityType.addEntity();

            entityId1 = entity1.getId();
            entityId2 = entity2.getId();
            entityId3 = entity3.getId();

            RoleType role1 = graph.putRoleType("role1");
            RoleType role2 = graph.putRoleType("role2");
            entityType.playsRole(role1).playsRole(role2);
            RelationType relationType = graph.putRelationType(related).hasRole(role1).hasRole(role2);

            RoleType role3 = graph.putRoleType("role3");
            RoleType role4 = graph.putRoleType("role4");
            entityType.playsRole(role3).playsRole(role4);
            relationType.playsRole(role3).playsRole(role4);
            RelationType relationType2 = graph.putRelationType(veryRelated).hasRole(role3).hasRole(role4);

            relationId12 = relationType.addRelation()
                    .putRolePlayer(role1, entity1)
                    .putRolePlayer(role2, entity2).getId();
            relationId13 = relationType.addRelation()
                    .putRolePlayer(role1, entity1)
                    .putRolePlayer(role2, entity3).getId();

            relationId1A12 = relationType2.addRelation()
                    .putRolePlayer(role3, entity1)
                    .putRolePlayer(role4, graph.getConcept(relationId12)).getId();

            graph.commitOnClose();
        }
        GraknSparkComputer.clear();
    }
}
