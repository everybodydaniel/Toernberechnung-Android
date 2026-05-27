package com.example.trnberechnung.repository

import com.example.trnberechnung.database.TideDao
import com.example.trnberechnung.database.TideEntity
import com.example.trnberechnung.model.*
import com.example.trnberechnung.network.BshApiService
import com.example.trnberechnung.network.RetrofitInstance
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import io.kotest.matchers.shouldBe
import retrofit2.Response

class TideRepositoryTest {

    private val tideDao = mockk<TideDao>(relaxed = true)
    private val logbookDao = mockk<LogbookDao>(relaxed = true)
    private val crewMemberDao = mockk<CrewMemberDao>(relaxed = true)
    private val checklistDao = mockk<ChecklistDao>(relaxed = true)

    private lateinit var repository: TideRepository

    @Before
    fun setup() {
        repository = TideRepository(tideDao, logbookDao, crewMemberDao, checklistDao)
    }

    @Test
    fun `getDataFromDatabase returns data from DAO`() = runTest {
        val mockData = listOf(TideEntity(1, "Norderney", "Region", 53.0, 7.0, 1.0, 2.0, 0.5, "2024-01-01"))
        coEvery { tideDao.getAll() } returns mockData

        val result = repository.getDataFromDatabase()

        result shouldBe mockData
        coVerify { tideDao.getAll() }
    }

    @Test
    fun `getDataFromApi handles network failure gracefully`() = runTest {
        // Gray Box / Integration: Testing how the repository handles a failing network component
        // We mock the static RetrofitInstance to simulate a network error
        mockkObject(RetrofitInstance)
        val mockApi = mockk<BshApiService>()
        every { RetrofitInstance.bshApi } returns mockApi

        coEvery { mockApi.getWaterLevel(any(), any()) } throws Exception("Network Down")

        val result = repository.getDataFromApi()

        result shouldBe emptyList() // Robustness/Non-functional: No crash on exception
        unmockkObject(RetrofitInstance)
    }
}
