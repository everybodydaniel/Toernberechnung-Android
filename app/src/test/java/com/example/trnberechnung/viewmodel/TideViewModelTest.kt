package com.example.trnberechnung.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.trnberechnung.model.ChecklistItem
import com.example.trnberechnung.model.CrewMember
import com.example.trnberechnung.model.LogbookEntry
import com.example.trnberechnung.repository.TideRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class TideViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<TideRepository>()

    private lateinit var viewModel: TideViewModel

    private fun setupViewModel() {
        every { repository.allLogs } returns flowOf(emptyList())
        every { repository.allCrew } returns flowOf(emptyList())
        viewModel = TideViewModel(repository)
    }

    @Test
    fun `saveCrew calls repository insertCrew`() = runTest {
        setupViewModel()
        val member = CrewMember(name = "Max", rank = "Skipper", isOnBoard = true, medicalNote = "", emergencyPhone = "")
        coEvery { repository.insertCrew(member) } returns Unit

        viewModel.saveCrew(member)
        coVerify(exactly = 1) { repository.insertCrew(member) }
    }

    @Test
    fun `deleteCrew calls repository deleteCrew`() = runTest {
        setupViewModel()
        val member = CrewMember(name = "Max", rank = "Skipper", isOnBoard = true, medicalNote = "", emergencyPhone = "")
        coEvery { repository.deleteCrew(member) } returns Unit

        viewModel.deleteCrew(member)
        coVerify(exactly = 1) { repository.deleteCrew(member) }
    }

    @Test
    fun `saveLog calls repository insertLog`() = runTest {
        setupViewModel()
        val log = LogbookEntry(date = "2024", routeDesc = "Test", distance = "10nm", duration = "2h", status = "ok", details = "")
        coEvery { repository.insertLog(log) } returns Unit

        viewModel.saveLog(log)
        coVerify(exactly = 1) { repository.insertLog(log) }
    }

    @Test
    fun `deleteLog calls repository deleteLog`() = runTest {
        setupViewModel()
        val log = LogbookEntry(date = "2024", routeDesc = "Test", distance = "10nm", duration = "2h", status = "ok", details = "")
        coEvery { repository.deleteLog(log) } returns Unit

        viewModel.deleteLog(log)
        coVerify(exactly = 1) { repository.deleteLog(log) }
    }

    @Test
    fun `deleteAllLogs calls repository deleteAllLogs`() = runTest {
        setupViewModel()
        coEvery { repository.deleteAllLogs() } returns Unit

        viewModel.deleteAllLogs()
        coVerify(exactly = 1) { repository.deleteAllLogs() }
    }
}
