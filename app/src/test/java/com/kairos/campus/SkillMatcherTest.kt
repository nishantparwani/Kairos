package com.kairos.campus
import org.junit.Assert.*
import org.junit.Test
class SkillMatcherTest {
 @Test fun matchesInheritanceToJava() { val java=Profile(id="j",skills="Java");val python=Profile(id="p",skills="Python");assertEquals(listOf(java),SkillMatcher.rank("help with inheritance",listOf(python,java))) }
 @Test fun noFalsePositiveForUnknownSkill() {assertTrue(SkillMatcher.rank("astronomy",listOf(Profile(skills="Kotlin"))).isEmpty())}
 @Test fun fillerWordsDoNotMatchBios() { val python=Profile(id="p",skills="Python");val maths=Profile(id="m",skills="Calculus",bio="Learn derivatives with practical examples");assertEquals(listOf(python),SkillMatcher.rank("help with python",listOf(maths,python))) }
 @Test fun matchesAbhikWithDsaAndMl() { val abhik=Profile(id="a",name="Abhik",skills="AI/ML, DSA, Python, Data Structures, Algorithms");val nishant=Profile(id="n",name="Nishant",skills="Video editing");assertEquals(listOf(abhik),SkillMatcher.rank("help with dsa",listOf(nishant,abhik)));assertEquals(listOf(abhik),SkillMatcher.rank("help with ml",listOf(nishant,abhik))) }
}
